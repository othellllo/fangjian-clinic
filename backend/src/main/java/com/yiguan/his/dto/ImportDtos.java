package com.yiguan.his.dto;

import java.util.List;
import java.util.Map;

/** Excel 导入相关的返回结构。 */
public final class ImportDtos {

    private ImportDtos() {
    }

    public record RowError(int row, String field, String message) {
    }

    /** 预览（只校验不落库）。 */
    public record PreviewResult(String type, int total, int valid, int invalid,
                                List<RowError> errors, List<Map<String, String>> sample) {
    }

    /** 执行结果。 */
    public record ExecuteResult(String type, int inserted, int updated, int skipped, int failed,
                                List<RowError> errors) {
    }
}
