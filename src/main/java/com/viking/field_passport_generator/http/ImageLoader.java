package com.viking.field_passport_generator.http;

import java.util.Collection;

public interface ImageLoader {
    LoadResult load(Collection<String> ids);
}

