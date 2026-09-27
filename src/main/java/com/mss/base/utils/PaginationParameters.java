package com.mss.base.utils;

import java.util.List;
import java.util.Map;

/**
 * Utility class for handling pagination parameters.
 */
public class PaginationParameters {

    /**
     * Populates the provided map with pagination details, including the current page, next page, previous page,
     * page size, total number of items, total pages, and the list of items for the current page.
     *
     * @param map   The map to populate with pagination details.
     * @param page  The current page number (0-based index).
     * @param total The total number of items available.
     * @param size  The number of items per page.
     * @param lists The list of items for the current page.
     */
    public static void getData(Map<String, Object> map, Integer page, Long total, Integer size, List<?> lists) {
        // Validate size to avoid division by zero or incorrect calculations
        if (size == null || size <= 0) {
            throw new IllegalArgumentException("Page size must be greater than zero.");
        }

        // Calculate total pages
        int totalPages = (int) Math.ceil((double) total / size);

        // Determine next and previous pages
        Integer nextPage = (page + 1 < totalPages) ? page + 1 : null;
        Integer previousPage = (page > 0) ? page - 1 : null;

        // Populate the map with pagination information
        map.put("currentPage", page);
        map.put("nextPage", nextPage);
        map.put("previousPage", previousPage);
        map.put("totalPages", totalPages);
        map.put("total", total);
        map.put("lists", lists);
    }
}