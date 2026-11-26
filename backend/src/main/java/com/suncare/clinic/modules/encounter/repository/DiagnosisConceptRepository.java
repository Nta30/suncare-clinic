package com.suncare.clinic.modules.encounter.repository;

import com.suncare.clinic.modules.encounter.dto.DiagnosisConceptResponse;
import com.suncare.clinic.modules.encounter.dto.DiagnosisConceptSearchResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.text.Normalizer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class DiagnosisConceptRepository {
    private static final String NAME = "REPLACE(REPLACE(d.name,N'đ',N'd'),N'Đ',N'D') COLLATE Latin1_General_100_CI_AI";
    private static final String ENGLISH = "d.english_name COLLATE Latin1_General_100_CI_AI";
    private static final String WORD_NAME = "(N' ' + TRANSLATE(REPLACE(REPLACE(d.name,N'đ',N'd'),N'Đ',N'D'),N'[](),.;:/-–—',REPLICATE(N' ',LEN(N'[](),.;:/-–—')))) COLLATE Latin1_General_100_CI_AI";
    private static final String WORD_ENGLISH = "(N' ' + TRANSLATE(d.english_name,N'[](),.;:/-–—',REPLICATE(N' ',LEN(N'[](),.;:/-–—')))) COLLATE Latin1_General_100_CI_AI";
    private static final String COLUMNS = """
            SELECT d.id,d.code_system,d.code_version,d.code,d.name,d.primary_allowed,d.english_name,
                   parent.code AS category_code,parent.name AS category_name
            FROM dbo.DiagnosisConcept d
            LEFT JOIN dbo.DiagnosisConcept parent ON parent.code_system=d.code_system
                AND parent.code_version=d.code_version AND parent.code=LEFT(d.code,3)
            """;
    private final JdbcTemplate jdbc;
    public DiagnosisConceptRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<DiagnosisConceptResponse> search(String text, int page) {
        return searchPage(text, page, false).items();
    }

    public DiagnosisConceptSearchResponse searchPage(String text, int page, boolean groups) {
        String normalized = Normalizer.normalize(text.strip(), Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replace('đ', 'd').replace('Đ', 'D').replaceAll("\\s+", " ");
        String compactCode = normalized.replaceAll("[\\s.]", "").toUpperCase(Locale.ROOT);
        StringBuilder where = new StringBuilder(groups
                ? " WHERE d.is_active=1 AND LEN(d.code)=3" : " WHERE d.is_active=1 AND d.is_selectable=1");
        List<Object> args = new ArrayList<>();
        if (compactCode.matches(groups ? "(?:[A-Z]|[A-Z][0-9][A-Z0-9]{0,5})" : "[A-Z][0-9][A-Z0-9]{0,5}")) {
            where.append(" AND REPLACE(d.code,'.','') LIKE ? ESCAPE '~'");
            args.add(escape(compactCode) + "%");
        } else if (!normalized.isEmpty()) {
            for (String token : normalized.replaceAll("[\\[\\](),.;:/–—-]+", " ").strip().split("\\s+")) {
                // Match from a word boundary: rang must not match trang (trắng).
                where.append(" AND (d.code LIKE ? ESCAPE '~' OR ").append(WORD_NAME)
                        .append(" LIKE ? ESCAPE '~' OR ").append(WORD_ENGLISH).append(" LIKE ? ESCAPE '~')");
                args.add("%" + escape(token) + "%");
                args.add("% " + escape(token) + "%");
                args.add("% " + escape(token) + "%");
            }
        }
        int total = jdbc.queryForObject("SELECT COUNT(*) FROM dbo.DiagnosisConcept d" + where + " OPTION (MAXDOP 1)", Integer.class, args.toArray());
        String order = " ORDER BY CASE WHEN REPLACE(d.code,'.','')=? THEN 0"
                + " WHEN REPLACE(d.code,'.','') LIKE ? ESCAPE '~' THEN 1"
                + " WHEN " + NAME + "=? OR " + ENGLISH + "=? THEN 2"
                + " WHEN " + NAME + " LIKE ? ESCAPE '~' OR " + ENGLISH + " LIKE ? ESCAPE '~' THEN 3"
                + " WHEN " + NAME + " LIKE ? ESCAPE '~' OR " + ENGLISH + " LIKE ? ESCAPE '~' THEN 4"
                + " ELSE 5 END,d.code,d.id OFFSET ? ROWS FETCH NEXT 20 ROWS ONLY OPTION (MAXDOP 1)";
        args.add(compactCode);
        args.add(escape(compactCode) + "%");
        args.add(normalized);
        args.add(normalized);
        args.add(escape(normalized) + "%");
        args.add(escape(normalized) + "%");
        args.add("%" + escape(normalized) + "%");
        args.add("%" + escape(normalized) + "%");
        args.add((long) (page - 1) * 20);
        var items = jdbc.query(COLUMNS + where + order, (rs, n) -> map(rs), args.toArray());
        return new DiagnosisConceptSearchResponse(items, page, 20, total, (total + 19) / 20);
    }

    public DiagnosisConceptResponse findSelectable(long id) {
        return jdbc.query(COLUMNS.replace("FROM dbo.DiagnosisConcept d", "FROM dbo.DiagnosisConcept d WITH (HOLDLOCK)")
                + " WHERE d.id=? AND d.is_active=1 AND d.is_selectable=1", (rs, n) -> map(rs), id)
                .stream().findFirst().orElse(null);
    }

    private DiagnosisConceptResponse map(ResultSet rs) throws SQLException {
        return new DiagnosisConceptResponse(rs.getLong("id"), rs.getString("code_system"), rs.getString("code_version"),
                rs.getString("code"), rs.getString("name"), rs.getBoolean("primary_allowed"), rs.getString("english_name"),
                rs.getString("category_code"), rs.getString("category_name"));
    }

    private String escape(String text) {
        return text.replace("~", "~~").replace("%", "~%").replace("_", "~_").replace("[", "~[");
    }
}
