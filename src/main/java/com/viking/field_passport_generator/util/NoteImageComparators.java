package com.viking.field_passport_generator.util;

import com.viking.field_passport_generator.model.NoteImage;

import java.util.Comparator;

public class NoteImageComparators {
    public static Comparator<NoteImage> byComplexIndex() {
        return (img1, img2) -> {
            String s1 = img1.getComplexIndex();
            String s2 = img2.getComplexIndex();

            int result = img1.getId().compareTo(img2.getId());
            if (s1.equals(s2)) {
                return result;
            }

            try {
                String[] p1 = s1.split("\\.");
                String[] p2 = s2.split("\\.");
                int length = Math.min(p1.length, p2.length);

                for (int i = 0; i < length; i++) {
                    int v1 = Integer.parseInt(p1[i]);
                    int v2 = Integer.parseInt(p2[i]);
                    if (v1 != v2) {
                        return Integer.compare(v1, v2);
                    }
                }
                if (p1.length != p2.length) {
                    return Integer.compare(p1.length, p2.length);
                }
            } catch (NumberFormatException e) {
                int res = s1.compareTo(s2);
                if (res != 0) return res;
            }
            return result;
        };
    }
}
