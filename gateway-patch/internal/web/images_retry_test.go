package web

import (
	"context"
	"errors"
	"net/http"
	"testing"
	"time"

	"m365-copilot2api/internal/auth"
	"m365-copilot2api/internal/chathub"
)

func imageRetryAccounts(t *testing.T) *auth.Store {
	t.Helper()
	store := testAccountFiles(t)
	for i, id := range []string{"u-1", "u-2", "u-3"} {
		if _, err := store.Upsert(auth.TokenSet{
			HomeOID:      id,
			Email:        []string{"one@example.com", "two@example.com", "three@example.com"}[i],
			AccessToken:  []string{"tok1", "tok2", "tok3"}[i],
			RefreshToken: []string{"r1", "r2", "r3"}[i],
			ExpiresAt:    time.Now().Add(time.Hour),
			TenantID:     "tid-" + id,
		}); err != nil {
			t.Fatal(err)
		}
	}
	return store
}

func TestImageRetryable(t *testing.T) {
	cases := []struct {
		name string
		err  error
		want bool
	}{
		{"nil", nil, false},
		{"canceled", context.Canceled, false},
		{"policy", chathub.ErrOffensiveContent, false},
		{"quota", chathub.ErrImageLimit, true},
		{"empty", chathub.ErrEmptyCompletion, true},
		{"rate", &UpstreamHTTPError{Status: http.StatusTooManyRequests}, true},
		{"auth", &UpstreamHTTPError{Status: http.StatusUnauthorized}, true},
		{"gateway", &UpstreamHTTPError{Status: http.StatusBadGateway}, true},
		{"edge", &chathub.DialError{Status: 520}, true},
		{"plain", errors.New("upstream returned no image resource"), true},
		{"other", errors.New("prompt is required"), false},
	}
	for _, tc := range cases {
		if got := imageRetryable(tc.err); got != tc.want {
			t.Fatalf("%s: got %v want %v", tc.name, got, tc.want)
		}
	}
}

func TestGenerateImageRetriesOnBadGatewayThenSwitchesAccount(t *testing.T) {
	store := imageRetryAccounts(t)
	first, ok := store.Get("u-1")
	if !ok {
		t.Fatal("missing u-1")
	}
	s := &Server{
		tokens:             store,
		accountPool:        newAccountHealth(),
		accountConcurrency: newAccountConcurrency(),
		settings:           &settingsStore{v: defaultRuntimeSettings()},
		chat: &chathub.Client{ChatOverride: func(account chathub.Account, _ chathub.Request) (chathub.Result, error) {
			if account.AccessToken == "tok1" {
				return chathub.Result{}, &chathub.DialError{Status: http.StatusBadGateway, Kind: "TCP"}
			}
			return chathub.Result{Text: "https://designer.example/image.png", Images: []string{"https://designer.example/image.png"}}, nil
		}},
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	res, acc, err := s.generateImageWithRetry(ctx, first, imageGenerationRequest{Prompt: "red circle"}, "draw")
	if err != nil {
		t.Fatalf("retry returned error: %v", err)
	}
	if acc.ID == first.ID {
		t.Fatalf("stayed on failed account %s", acc.ID)
	}
	if len(res.Images) != 1 {
		t.Fatalf("images=%v", res.Images)
	}
	if s.accountPool.Available(first.ID) {
		t.Fatal("failed account should be cooling down")
	}
}

func TestGenerateImageDoesNotSwitchPinnedAccount(t *testing.T) {
	store := imageRetryAccounts(t)
	first, ok := store.Get("u-1")
	if !ok {
		t.Fatal("missing u-1")
	}
	calls := 0
	s := &Server{
		tokens:             store,
		accountPool:        newAccountHealth(),
		accountConcurrency: newAccountConcurrency(),
		settings:           &settingsStore{v: defaultRuntimeSettings()},
		chat: &chathub.Client{ChatOverride: func(account chathub.Account, _ chathub.Request) (chathub.Result, error) {
			calls++
			return chathub.Result{}, &chathub.DialError{Status: http.StatusBadGateway, Kind: "TCP"}
		}},
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_, acc, err := s.generateImageWithRetry(ctx, first, imageGenerationRequest{Prompt: "red circle", AccountID: first.ID}, "draw")
	if err == nil {
		t.Fatal("pinned account should surface the upstream error")
	}
	if acc.ID != first.ID {
		t.Fatalf("pinned account changed to %s", acc.ID)
	}
	if calls != 1 {
		t.Fatalf("pinned account was retried %d times", calls)
	}
}
