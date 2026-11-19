package com.yiguan.his.service;

import com.yiguan.his.domain.MedicalTerm;
import com.yiguan.his.domain.enums.TermCategory;
import com.yiguan.his.dto.QcResult;
import com.yiguan.his.repository.MedicalTermRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 联想输入：标准术语库 + 本院历史病历高频短语。
 * 全部本地查询，不依赖外网。
 */
@Service
public class TermService {

    private final MedicalTermRepository termRepository;
    private final JdbcTemplate jdbc;

    public TermService(MedicalTermRepository termRepository, JdbcTemplate jdbc) {
        this.termRepository = termRepository;
        this.jdbc = jdbc;
    }

    public List<QcResult.Suggestion> suggest(TermCategory category, String keyword, int limit) {
        int size = Math.max(1, Math.min(limit, 30));
        String kw = keyword == null ? "" : keyword.trim();
        Set<String> seen = new LinkedHashSet<>();
        List<QcResult.Suggestion> result = new ArrayList<>();

        for (MedicalTerm term : termRepository.suggest(category, kw, PageRequest.of(0, size))) {
            if (seen.add(term.getTerm())) {
                result.add(new QcResult.Suggestion(term.getTerm(), term.getPinyin(),
                        term.getCategory().name(), term.getSource(), term.getUsageCount()));
            }
        }
        // 历史病历补充
        String column = historyColumn(category);
        if (column != null && result.size() < size) {
            String sql = "select distinct " + column + " from medical_record where " + column
                    + " is not null and " + column + " <> ''";
            if (!kw.isEmpty()) {
                sql += " and " + column + " like ?";
            }
            sql += " order by " + column;
            List<String> rows = kw.isEmpty()
                    ? jdbc.queryForList(sql, String.class)
                    : jdbc.queryForList(sql, String.class, "%" + kw + "%");
            for (String row : rows) {
                if (row == null) {
                    continue;
                }
                String snippet = row.length() > 60 ? row.substring(0, 60) : row;
                if (seen.add(snippet)) {
                    result.add(new QcResult.Suggestion(snippet, null, category.name(), "HISTORY", 0));
                }
                if (result.size() >= size) {
                    break;
                }
            }
        }
        return result;
    }

    private String historyColumn(TermCategory category) {
        return switch (category) {
            case SYMPTOM -> "chief_complaint";
            case SYNDROME -> "syndrome";
            case TREATMENT -> "treatment_principle";
            case DIAGNOSIS -> "western_diagnosis";
            case TONGUE -> "tongue";
            case PULSE -> "pulse";
            default -> null;
        };
    }

    /** 术语被采用后累加使用次数，让常用项排前面。 */
    public void touch(TermCategory category, String term) {
        termRepository.findAll().stream()
                .filter(item -> item.getCategory() == category && item.getTerm().equals(term))
                .findFirst()
                .ifPresent(item -> {
                    item.setUsageCount((item.getUsageCount() == null ? 0 : item.getUsageCount()) + 1);
                    termRepository.save(item);
                });
    }
}
