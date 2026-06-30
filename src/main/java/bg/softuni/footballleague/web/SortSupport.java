package bg.softuni.footballleague.web;

import org.springframework.data.domain.Sort;

import java.util.Map;

public final class SortSupport {

    private SortSupport() {
    }

    public static Sort resolve(String sortKey, String dir, Map<String, String> allowedFields, Sort defaultSort) {
        if (sortKey == null || !allowedFields.containsKey(sortKey)) {
            return defaultSort;
        }
        Sort.Direction direction = "desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, allowedFields.get(sortKey));
    }
}
