package com.viking.field_passport_generator.util;

import com.viking.field_passport_generator.model.NoteImage;

import java.util.Comparator;

public class NoteImageComparators {
    public static Comparator<NoteImage> byComplexIndex() {
        return (img1, img2) -> {
            String s1 = img1.complexIndex();
            String s2 = img2.complexIndex();

            if (!s1.contains(".") || !s2.contains(".")) {
                return s1.compareTo(s2);
            }

            try {
                String[] p1 = s1.split("\\.");
                String[] p2 = s2.split("\\.");

                int major1 = Integer.parseInt(p1[0]);
                int major2 = Integer.parseInt(p2[0]);
                if (major1 != major2) return Integer.compare(major1, major2);

                int minor1 = Integer.parseInt(p1[1]);
                int minor2 = Integer.parseInt(p2[1]);
                return Integer.compare(minor1, minor2);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                return s1.compareTo(s2);
            }
        };
    }
}
