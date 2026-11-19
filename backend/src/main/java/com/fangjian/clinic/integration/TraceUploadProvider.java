package com.fangjian.clinic.integration;

import com.fangjian.clinic.dto.TraceDto;

import java.util.List;

/**
 * 药品追溯码上传扩展点（SPI）。
 *
 * <p>国家医保局正在建设全国统一的追溯码上传接口，各省也存在本地平台；
 * 接入方式差异大，因此系统只定义契约，由实施方按当地规范实现 Bean。</p>
 */
public interface TraceUploadProvider {

    String code();

    boolean enabled();

    UploadResult upload(List<TraceDto> traces);

    record UploadResult(String status, String message) {
    }
}
