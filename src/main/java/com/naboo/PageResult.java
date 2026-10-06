package com.naboo;

import java.util.List;

public record PageResult<T>(List<T> items, int totalItems, int totalPages) {}
