package app.inkbench.studio;

import java.util.ArrayList;
import java.util.List;

/** Undo is scoped to AI replacements; manual typing never destroys the saved original. */
public final class PromptHistory {
    private final ArrayList<String> steps = new ArrayList<>();
    private String original;
    public void beforeReplace(String current, String replacement) {
        if (current.equals(replacement)) return;
        if (original == null) original = current;
        if (steps.size() == 8) steps.remove(0);
        steps.add(current);
    }
    public boolean canUndo() { return original != null; }
    public String undo() {
        if (!steps.isEmpty()) {
            String value = steps.remove(steps.size()-1);
            if (steps.isEmpty() && value.equals(original)) original = null;
            return value;
        }
        return restoreOriginal();
    }
    public String restoreOriginal() {
        String value = original;
        steps.clear(); original = null;
        return value;
    }
    public String original() { return original; }
    public List<String> steps() { return new ArrayList<>(steps); }
    public void load(String first, List<String> saved) {
        original = first; steps.clear();
        if (first != null) steps.addAll(saved.subList(Math.max(0,saved.size()-8),saved.size()));
    }
}
