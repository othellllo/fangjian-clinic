package com.yiguan.his.integration;

import com.yiguan.his.dto.TraceDto;
import org.springframework.stereotype.Component;

import java.util.List;

/** 未接入追溯码上传平台时的默认实现：返回 UNSUPPORTED，数据留在本地待补传。 */
@Component
public class NoopTraceUploadProvider implements TraceUploadProvider {

    @Override
    public String code() {
        return "noop";
    }

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public UploadResult upload(List<TraceDto> traces) {
        return new UploadResult("UNSUPPORTED", "当前未接入医保追溯码上传平台，数据已保存在本地，可导出后补传");
    }
}
