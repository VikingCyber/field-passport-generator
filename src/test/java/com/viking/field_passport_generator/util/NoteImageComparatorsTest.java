package com.viking.field_passport_generator.util;

import com.viking.field_passport_generator.model.media.NoteImage;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoteImageComparatorsTest {

    private static final Comparator<NoteImage> comparator = NoteImageComparators.byComplexIndex();

    @Test
    void compare_complexIndexes_shouldCompareByMajorThenMinor() {
        // Arrange
        NoteImage img1 = new NoteImage("123", "2.1");
        NoteImage img2 = new NoteImage("333", "10.1");

        // Act
        int result = comparator.compare(img1, img2);

        // Assert
        assertTrue(result < 0);
    }

    @Test
    void compare_sameMajorDifferentMinor_shouldCompareMinor() {
        // Arrange
        NoteImage img1 = new NoteImage("aaa123", "3.2");
        NoteImage img2 = new NoteImage("aabb333", "3.10");

        // Act
        int result = comparator.compare(img1, img2);

        // Assert
        assertTrue(result < 0);
    }

    @Test
    void compare_equalIndices_shouldReturnZero() {
        // Arrange
        NoteImage img1 = new NoteImage("same-id", "5.7");
        NoteImage img2 = new NoteImage("same-id", "5.7");

        // Act
        int result = comparator.compare(img1, img2);

        // Assert
        assertEquals(0, result);
    }

    @Test
    void compare_equalIndices_shouldCompareById() {
        // Arrange
        NoteImage img1 = new NoteImage("bbbb", "5.7");
        NoteImage img2 = new NoteImage("cccc", "5.7");

        // Act
        int result = comparator.compare(img1, img2);

        // Assert
        assertTrue(result < 0, "Should compare by ID if indices are equal");
    }

    @Test
    void compare_withoutDot_shouldCompareNumerically() {
        // Arrange
        NoteImage img1 = new NoteImage("cccc", "2");
        NoteImage img2 = new NoteImage("rrrr", "10");

        // Act
        int result = comparator.compare(img1, img2);

        // Assert
        assertTrue(result < 0);
    }
}
