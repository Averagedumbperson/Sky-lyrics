package com.skylyrics.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/** Greedy word wrapping with a caller-supplied width function (so it can use Minecraft's font). */
public final class TextWrap {
    private TextWrap() {
    }

    public static List<List<String>> wrapWords(String[] words, int maxWidth, ToIntFunction<String> measure) {
        List<List<String>> rows = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        for (String w : words) {
            if (w.isEmpty()) {
                continue;
            }
            if (!cur.isEmpty()) {
                List<String> trial = new ArrayList<>(cur);
                trial.add(w);
                if (measure.applyAsInt(String.join(" ", trial)) > maxWidth) {
                    rows.add(cur);
                    cur = new ArrayList<>();
                }
            }
            cur.add(w);
        }
        if (!cur.isEmpty()) {
            rows.add(cur);
        }
        return rows;
    }
}
