package com.processing.api.support;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Greedy covering array: every pair of binary parameter-presence levels occurs in at least one row. */
public final class Pairwise {
    private Pairwise() { }

    public static List<Integer> rows(int factors) {
        if (factors < 2 || factors > 12) {
            throw new IllegalArgumentException("Expected 2..12 binary factors");
        }
        Set<String> uncovered = new HashSet<>();
        for (int mask = 0; mask < (1 << factors); mask++) {
            uncovered.addAll(pairs(mask, factors));
        }
        List<Integer> rows = new ArrayList<>();
        while (!uncovered.isEmpty()) {
            int best = 0;
            long bestScore = -1;
            for (int mask = 0; mask < (1 << factors); mask++) {
                long score = pairs(mask, factors).stream().filter(uncovered::contains).count();
                if (score > bestScore) {
                    best = mask;
                    bestScore = score;
                }
            }
            rows.add(best);
            uncovered.removeAll(pairs(best, factors));
        }
        return rows;
    }

    private static Set<String> pairs(int mask, int factors) {
        Set<String> pairs = new HashSet<>();
        for (int first = 0; first < factors; first++) {
            for (int second = first + 1; second < factors; second++) {
                pairs.add(first + ":" + ((mask >> first) & 1) + ":" + second + ":" + ((mask >> second) & 1));
            }
        }
        return pairs;
    }
}
