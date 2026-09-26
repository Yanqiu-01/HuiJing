package web

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"m365-copilot2api/internal/auth"
	"m365-copilot2api/internal/chathub"
	"m365-copilot2api/internal/outbound"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"
)

const (
	designerAppServiceScope  = "https://designerappservice.officeapps.live.com/.default"
	maxGeneratedImageBytes   = 20 << 20
	maxImageEditRequestBytes = maxGeneratedImageBytes + (2 << 20)
	generatedImageTTL        = 15 * time.Minute
	maxGeneratedImages       = 128
)

type generatedImage struct {
	Data        []byte
	ContentType string
	ExpiresAt   time.Time
}

type imageGenerationRequest struct {
	Prompt         string               `json:"prompt"`
	N              int                  `json:"n"`
	Size           string               `json:"size"`
	ResponseFormat string               `json:"response_format"`
	Model          string               `json:"model"`
	AccountID      string               `json:"accountId"`
	User           string               `json:"user"`
	Operation      string               `json:"operation,omitempty"`
	Attachments    []chathub.Attachment `json:"attachments,omitempty"`
}

func (s *Server) imageGenerations(w http.ResponseWriter, r *http.Request) {
	startedAt := time.Now()
	if r.Method != http.MethodPost {
		writeOpenAIError(w, http.StatusMethodNotAllowed, "invalid_request_error", "method not allowed")
		return
	}
	var b imageGenerationRequest
	r.Body = http.MaxBytesReader(w, r.Body, maxImageEditRequestBytes)
	if json.NewDecoder(r.Body).Decode(&b) != nil || strings.TrimSpace(b.Prompt) == "" {
		writeOpenAIError(w, http.StatusBadRequest, "invalid_request_error", "prompt is required")
		return
	}
	if b.N <= 0 {
		b.N = 1
	}
	if b.N > 10 {
		writeOpenAIError(w, 400, "invalid_request_error", "n must be between 1 and 10")
		return
	}
	format := strings.ToLower(strings.TrimSpace(b.ResponseFormat))
	if format == "" {
		format = "url"
	}
	if format != "url" && format != "b64_json" {
		writeOpenAIError(w, http.StatusBadRequest, "invalid_request_error", "response_format must be url or b64_json")
		return
	}
	acc, err := s.resolveAccount(firstNonEmpty(b.AccountID, b.User))
	if err != nil {
		writeUpstreamError(w, err)
		return
	}
	if acc.OID == "" || acc.TID == "" {
		acc.OID, acc.TID = extractOIDTID(acc.AccessToken)
	}
	if acc.OID == "" || acc.TID == "" {
		writeOpenAIError(w, 400, "invalid_request_error", "account missing oid/tid — re-login with PKCE")
		return
	}
	ctx, cancel := context.WithTimeout(r.Context(), time.Duration(s.settings.get().ImageTimeoutSeconds)*time.Second)
	defer cancel()
	size := b.Size
	if size == "" {
		size = "1024x1024"
	}
	endpoint := "/v1/images/generations"
	prompt := fmt.Sprintf("Generate an image with GPT Image 2. Size: %s. Description: %s. Return the image URL directly.", size, b.Prompt)
	if b.Operation == "edit" {
		if len(b.Attachments) == 0 {
			writeOpenAIError(w, http.StatusBadRequest, "invalid_request_error", "image is required")
			return
		}
		endpoint = "/v1/images/edits"
		prompt = fmt.Sprintf("Edit the first attached image with GPT Image 2. Size: %s. Instructions: %s. Preserve everything not requested to change. Return the edited image URL directly.", size, b.Prompt)
	}
	res, acc, err := s.generateImageWithRetry(ctx, acc, b, prompt)
	if err != nil {
		writeUpstreamError(w, err)
		return
	}
	log.Printf("[image-gen] conversation=%s images=%d text_len=%d events=%d raw_len=%d account=%s", res.ConversationID, len(res.Images), len(res.Text), len(res.Events), len(res.RawResult), acc.ID)
	images := res.Images
	if len(images) > b.N {
		images = images[:b.N]
	}

	var designerToken string
	data := make([]map[string]string, 0, len(images))
	for _, sourceURL := range images {
		if strings.HasPrefix(strings.ToLower(sourceURL), "data:image/") {
			if format == "b64_json" {
				parts := strings.SplitN(sourceURL, ",", 2)
				if len(parts) != 2 {
					writeOpenAIError(w, http.StatusBadGateway, "upstream_error", "invalid upstream image data")
					return
				}
				data = append(data, map[string]string{"b64_json": parts[1]})
			} else {
				data = append(data, map[string]string{"url": sourceURL})
			}
			continue
		}
		if !isDesignerImageURL(sourceURL) {
			if format == "b64_json" {
				writeOpenAIError(w, http.StatusBadGateway, "unsupported_response_format", "upstream returned URL, not b64_json")
				return
			}
			data = append(data, map[string]string{"url": sourceURL})
			continue
		}
		if designerToken == "" {
			designerToken, err = s.designerAccessToken(acc)
			if err != nil {
				writeOpenAIError(w, http.StatusBadGateway, "upstream_error", upstreamError(err))
				return
			}
		}
		imageData, contentType, err := downloadDesignerImage(ctx, sourceURL, designerToken)
		if err != nil {
			log.Printf("[image-gen-download] err=%v", err)
			writeOpenAIError(w, http.StatusBadGateway, "upstream_error", upstreamError(err))
			return
		}
		if format == "b64_json" {
			data = append(data, map[string]string{"b64_json": base64.StdEncoding.EncodeToString(imageData)})
			continue
		}
		id := s.storeGeneratedImage(imageData, contentType)
		data = append(data, map[string]string{"url": generatedImageURL(r, id)})
	}

	s.usage.record(UsageRecord{
		Time:         time.Now(),
		APIKeyPrefix: extractAPIKey(r),
		AccountEmail: acc.Email,
		Model:        firstNonEmpty(b.Model, "gpt-image-2"),
		Endpoint:     endpoint,
		InputTokens:  EstimateTokens(prompt),
		DurationMs:   time.Since(startedAt).Milliseconds(),
		Status:       200,
	})
	jsonOut(w, map[string]any{"created": time.Now().Unix(), "data": data, "m365": map[string]any{"conversationId": res.ConversationID, "sessionId": res.SessionID, "images": images}})
}

const imageGenerationAttempts = 3

func imageRetryable(err error) bool {
	if err == nil || errors.Is(err, context.Canceled) || errors.Is(err, chathub.ErrOffensiveContent) {
		return false
	}
	if errors.Is(err, chathub.ErrImageLimit) || IsRateLimited(err) || IsAuthFailure(err) || IsEmptyCompletion(err) {
		return true
	}
	var dialErr *chathub.DialError
	if errors.As(err, &dialErr) && (dialErr.Status == http.StatusBadGateway || dialErr.Status == 520 || dialErr.Status == 524) {
		return true
	}
	var httpErr *UpstreamHTTPError
	if errors.As(err, &httpErr) && (httpErr.Status == http.StatusBadGateway || httpErr.Status == 520 || httpErr.Status == 524) {
		return true
	}
	msg := strings.ToLower(err.Error())
	return strings.Contains(msg, "bad gateway") || strings.Contains(msg, "no image resource") || strings.Contains(msg, "empty completion") || strings.Contains(msg, "upstream 502") || strings.Contains(msg, "upstream 520")
}

func (s *Server) noteImageFailure(accountID string, err error) {
	if s == nil || s.accountPool == nil || accountID == "" || err == nil {
		return
	}
	if errors.Is(err, chathub.ErrImageLimit) {
		s.accountPool.MarkImageLimited(accountID)
	}
	s.accountPool.MarkFailure(accountID, err, s.getRateLimitCooldown())
}

func (s *Server) nextImageAccount(avoidID string) (auth.AccountToken, error) {
	for i := 0; i < maxAccountProbe; i++ {
		next, err := s.nextHealthyAccount(avoidID)
		if err != nil {
			return auth.AccountToken{}, err
		}
		if next.OID == "" || next.TID == "" {
			next.OID, next.TID = extractOIDTID(next.AccessToken)
		}
		if next.OID == "" || next.TID == "" {
			log.Printf("[image-gen-retry] skip account %s: missing oid/tid", next.ID)
			s.noteImageFailure(next.ID, fmt.Errorf("account missing oid/tid"))
			avoidID = next.ID
			continue
		}
		return next, nil
	}
	return auth.AccountToken{}, fmt.Errorf("no healthy account available for failover")
}

func (s *Server) generateImageWithRetry(ctx context.Context, first auth.AccountToken, body imageGenerationRequest, prompt string) (chathub.Result, auth.AccountToken, error) {
	pinned := strings.TrimSpace(body.AccountID) != ""
	acc := first
	var lastErr error
	for attempt := 1; attempt <= imageGenerationAttempts; attempt++ {
		if err := ctx.Err(); err != nil {
			if lastErr != nil {
				return chathub.Result{}, acc, lastErr
			}
			return chathub.Result{}, acc, err
		}
		res, err := s.chatWithAccount(ctx, acc.ID, chathub.Account{AccessToken: acc.AccessToken, OID: acc.OID, TID: acc.TID}, chathub.Request{
			Text:         prompt,
			Tone:         "magic",
			Attachments:  body.Attachments,
			LicenseType:  s.settings.get().LicenseType,
			Scenario:     s.settings.get().Scenario,
			FeatureFlags: s.featureFlags(),
		})
		if err == nil && len(res.Images) == 0 {
			if urls := extractImageURLs(res.RawResult); len(urls) > 0 {
				res.Images = urls
			}
		}
		if err == nil && len(res.Images) == 0 {
			if urls := extractImageURLs(res.Text); len(urls) > 0 {
				res.Images = urls
			}
		}
		if err == nil && len(res.Images) == 0 {
			refusal := strings.Join([]string{res.Text, res.RawResult}, "\n")
			if isImageQuotaRefusal(refusal) {
				err = chathub.ErrImageLimit
			} else {
				err = fmt.Errorf("upstream returned no image resource")
			}
		}
		if err == nil {
			if attempt > 1 {
				log.Printf("[image-gen-retry] recovered on attempt %d account=%s", attempt, acc.ID)
			}
			return res, acc, nil
		}
		lastErr = err
		s.noteImageFailure(acc.ID, err)
		if pinned || !imageRetryable(err) || attempt == imageGenerationAttempts {
			log.Printf("[image-gen-retry] stop attempt=%d account=%s retryable=%t err=%v", attempt, acc.ID, imageRetryable(err), err)
			return chathub.Result{}, acc, err
		}
		next, nerr := s.nextImageAccount(acc.ID)
		if nerr != nil {
			log.Printf("[image-gen-retry] no alternate account after attempt %d: %v", attempt, nerr)
			return chathub.Result{}, acc, err
		}
		log.Printf("[image-gen-retry] attempt %d failed account=%s; switching to %s: %v", attempt, acc.ID, next.ID, err)
		acc = next
	}
	return chathub.Result{}, acc, lastErr
}

func (s *Server) imageEdits(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeOpenAIError(w, http.StatusMethodNotAllowed, "invalid_request_error", "method not allowed")
		return
	}
	r.Body = http.MaxBytesReader(w, r.Body, maxImageEditRequestBytes)
	if err := r.ParseMultipartForm(1 << 20); err != nil {
		writeOpenAIError(w, http.StatusBadRequest, "invalid_request_error", "invalid multipart image edit request")
		return
	}
	if r.MultipartForm != nil {
		defer r.MultipartForm.RemoveAll()
	}
	prompt := strings.TrimSpace(r.FormValue("prompt"))
	if prompt == "" {
		writeOpenAIError(w, http.StatusBadRequest, "invalid_request_error", "prompt is required")
		return
	}
	file, header, err := r.FormFile("image")
	if err != nil {
		file, header, err = r.FormFile("image[]")
	}
	if err != nil {
		writeOpenAIError(w, http.StatusBadRequest, "invalid_request_error", "image is required")
		return
	}
	defer file.Close()
	imageData, err := io.ReadAll(io.LimitReader(file, maxGeneratedImageBytes+1))
	if err != nil {
		writeOpenAIError(w, http.StatusBadRequest, "invalid_request_error", "could not read image")
		return
	}
	if len(imageData) > maxGeneratedImageBytes {
		writeOpenAIError(w, http.StatusRequestEntityTooLarge, "invalid_request_error", "image exceeds 20 MiB")
		return
	}
	contentType := http.DetectContentType(imageData)
	ext := ""
	switch contentType {
	case "image/png":
		ext = "png"
	case "image/jpeg":
		ext = "jpg"
	case "image/webp":
		ext = "webp"
	default:
		writeOpenAIError(w, http.StatusBadRequest, "invalid_request_error", "image must be PNG, JPEG, or WebP")
		return
	}
	name := strings.TrimSpace(header.Filename)
	if name == "" {
		name = "image." + ext
	}
	n := 1
	if rawN := strings.TrimSpace(r.FormValue("n")); rawN != "" {
		n, err = strconv.Atoi(rawN)
		if err != nil {
			writeOpenAIError(w, http.StatusBadRequest, "invalid_request_error", "n must be an integer")
			return
		}
	}
	body := imageGenerationRequest{
		Prompt:         prompt,
		N:              n,
		Size:           strings.TrimSpace(r.FormValue("size")),
		ResponseFormat: strings.TrimSpace(r.FormValue("response_format")),
		Model:          strings.TrimSpace(r.FormValue("model")),
		AccountID:      firstNonEmpty(strings.TrimSpace(r.FormValue("accountId")), strings.TrimSpace(r.FormValue("account_id"))),
		User:           strings.TrimSpace(r.FormValue("user")),
		Operation:      "edit",
		Attachments: []chathub.Attachment{{
			Type:     "image",
			URL:      "data:" + contentType + ";base64," + base64.StdEncoding.EncodeToString(imageData),
			Name:     name,
			MimeType: contentType,
		}},
	}
	encoded, err := json.Marshal(body)
	if err != nil {
		writeOpenAIError(w, http.StatusInternalServerError, "internal_error", "could not encode image edit request")
		return
	}
	next := r.Clone(r.Context())
	next.Body = io.NopCloser(bytes.NewReader(encoded))
	next.ContentLength = int64(len(encoded))
	next.Header = r.Header.Clone()
	next.Header.Set("Content-Type", "application/json")
	next.Form = nil
	next.PostForm = nil
	next.MultipartForm = nil
	s.imageGenerations(w, next)
}

func (s *Server) designerAccessToken(acc auth.AccountToken) (string, error) {
	if strings.TrimSpace(acc.RefreshToken) == "" {
		return "", fmt.Errorf("account has no refresh token for Designer image download")
	}
	clientID := firstNonEmpty(acc.ClientID, auth.ClientID())
	set, err := auth.RefreshWithScope(acc.RefreshToken, clientID, designerAppServiceScope)
	if err != nil {
		return "", fmt.Errorf("obtain Designer image token: %w", err)
	}
	if set.RefreshToken != "" && set.RefreshToken != acc.RefreshToken {
		if err := s.tokens.UpdateRefreshToken(acc.ID, set.RefreshToken); err != nil {
			log.Printf("[image-gen] rotated refresh token could not be persisted account=%s err=%v", acc.ID, err)
		}
	}
	return set.AccessToken, nil
}

func isDesignerImageURL(raw string) bool {
	u, err := url.Parse(raw)
	return err == nil && strings.EqualFold(u.Scheme, "https") && strings.EqualFold(u.Hostname(), "designerapp.officeapps.live.com")
}

func downloadDesignerImage(ctx context.Context, rawURL, accessToken string) ([]byte, string, error) {
	if !isDesignerImageURL(rawURL) {
		return nil, "", fmt.Errorf("unsupported generated image host")
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, nil)
	if err != nil {
		return nil, "", err
	}
	req.Header.Set("Authorization", "Bearer "+accessToken)
	req.Header.Set("Accept", "image/*")
	client := *outbound.HTTPClient()
	client.CheckRedirect = func(next *http.Request, via []*http.Request) error {
		if len(via) >= 3 || !isDesignerImageURL(next.URL.String()) {
			return http.ErrUseLastResponse
		}
		return nil
	}
	resp, err := client.Do(req)
	if err != nil {
		return nil, "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, "", fmt.Errorf("Designer image download HTTP %d", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxGeneratedImageBytes+1))
	if err != nil {
		return nil, "", err
	}
	if len(body) > maxGeneratedImageBytes {
		return nil, "", fmt.Errorf("generated image exceeds %d bytes", maxGeneratedImageBytes)
	}
	contentType := resp.Header.Get("Content-Type")
	if contentType == "" || !strings.HasPrefix(strings.ToLower(contentType), "image/") {
		contentType = http.DetectContentType(body)
	}
	if !strings.HasPrefix(strings.ToLower(contentType), "image/") {
		return nil, "", fmt.Errorf("Designer returned non-image content")
	}
	return body, contentType, nil
}

func (s *Server) storeGeneratedImage(data []byte, contentType string) string {
	id := uuid.NewString()
	now := time.Now()
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.generatedImages == nil {
		s.generatedImages = map[string]generatedImage{}
	}
	for key, item := range s.generatedImages {
		if now.After(item.ExpiresAt) {
			delete(s.generatedImages, key)
		}
	}
	if len(s.generatedImages) >= maxGeneratedImages {
		var oldestID string
		var oldest time.Time
		for key, item := range s.generatedImages {
			if oldestID == "" || item.ExpiresAt.Before(oldest) {
				oldestID, oldest = key, item.ExpiresAt
			}
		}
		if oldestID != "" {
			delete(s.generatedImages, oldestID)
		}
	}
	s.generatedImages[id] = generatedImage{Data: append([]byte(nil), data...), ContentType: contentType, ExpiresAt: now.Add(generatedImageTTL)}
	return id
}

func generatedImageURL(r *http.Request, id string) string {
	scheme := "http"
	if r.TLS != nil || strings.EqualFold(r.Header.Get("X-Forwarded-Proto"), "https") {
		scheme = "https"
	}
	return fmt.Sprintf("%s://%s/v1/images/files/%s", scheme, r.Host, id)
}

func (s *Server) generatedImageFile(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeOpenAIError(w, http.StatusMethodNotAllowed, "invalid_request_error", "method not allowed")
		return
	}
	id := strings.TrimPrefix(r.URL.Path, "/v1/images/files/")
	if _, err := uuid.Parse(id); err != nil {
		http.NotFound(w, r)
		return
	}
	now := time.Now()
	s.mu.Lock()
	item, ok := s.generatedImages[id]
	if ok && now.After(item.ExpiresAt) {
		delete(s.generatedImages, id)
		ok = false
	}
	if ok {
		item.Data = append([]byte(nil), item.Data...)
	}
	s.mu.Unlock()
	if !ok {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", item.ContentType)
	w.Header().Set("Cache-Control", "private, max-age=300")
	w.Header().Set("Content-Length", fmt.Sprint(len(item.Data)))
	_, _ = w.Write(item.Data)
}

func isImageQuotaRefusal(text string) bool {
	low := strings.ToLower(strings.TrimSpace(text))
	for _, phrase := range []string{
		"generate any more images",
		"image generation quota",
		"daily image limit",
		"try again tomorrow",
		"无法再生成图片",
		"请明天再试",
	} {
		if strings.Contains(low, phrase) {
			return true
		}
	}
	return false
}

// extractImageURLs finds image URLs in a raw JSON string by searching for URL patterns.
func extractImageURLs(raw string) []string {
	if raw == "" {
		return nil
	}
	var out []string
	seen := map[string]bool{}
	var v any
	if err := json.Unmarshal([]byte(raw), &v); err != nil {
		return nil
	}
	var walk func(any)
	walk = func(v any) {
		switch x := v.(type) {
		case []any:
			for _, e := range x {
				walk(e)
			}
		case map[string]any:
			for k, e := range x {
				lk := strings.ToLower(k)
				if s, ok := e.(string); ok && (lk == "url" || lk == "imageurl" || lk == "thumbnailurl" || lk == "downloadurl" || lk == "src" || lk == "value" || lk == "data") {
					if strings.HasPrefix(s, "https://") && !seen[s] {
						if strings.Contains(strings.ToLower(s), "image") || strings.HasSuffix(strings.ToLower(s), ".png") || strings.HasSuffix(strings.ToLower(s), ".jpg") || strings.HasSuffix(strings.ToLower(s), ".jpeg") || strings.HasSuffix(strings.ToLower(s), ".webp") || strings.HasSuffix(strings.ToLower(s), ".gif") {
							seen[s] = true
							out = append(out, s)
						}
					}
				} else {
					walk(e)
				}
			}
		}
	}
	walk(v)
	return out
}

func downloadImageAsBase64(url string) (b64, contentType string, err error) {
	return downloadImageAsBase64WithToken(url, "")
}

func downloadImageAsBase64WithToken(url, token string) (b64, contentType string, err error) {
	ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return "", "", err
	}
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return "", "", fmt.Errorf("download returned %d", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 20<<20))
	if err != nil {
		return "", "", err
	}
	ct := resp.Header.Get("Content-Type")
	if ct == "" {
		ct = http.DetectContentType(body)
	}
	enc := base64.StdEncoding.EncodeToString(body)
	return enc, ct, nil
}

func downloadImageAsDataURI(url string) (string, error) {
	b64, ct, err := downloadImageAsBase64(url)
	if err != nil {
		return url, nil
	}
	return "data:" + ct + ";base64," + b64, nil
}

func downloadImageAsDataURIWithToken(url, token string) (string, error) {
	b64, ct, err := downloadImageAsBase64WithToken(url, token)
	if err != nil {
		urlPreview := url
		if len(urlPreview) > 80 {
			urlPreview = urlPreview[:80]
		}
		log.Printf("[image-download] failed url=%s token_len=%d err=%v", urlPreview, len(token), err)
		return url, nil
	}
	urlPreview := url
	if len(urlPreview) > 80 {
		urlPreview = urlPreview[:80]
	}
	log.Printf("[image-download] ok url=%s ct=%s size=%d", urlPreview, ct, len(b64))
	return "data:" + ct + ";base64," + b64, nil
}
