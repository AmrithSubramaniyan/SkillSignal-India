package in.skillsignal.common;

import java.util.Collections;
import java.util.List;

public record ApiErrorResponse(String error, List<String> details) {
    public static ApiErrorResponse of(String error) {
        return new ApiErrorResponse(error, Collections.emptyList());
    }

    public static ApiErrorResponse of(String error, List<String> details) {
        return new ApiErrorResponse(error, details == null ? Collections.emptyList() : details);
    }
}

