package com.naengo.api_server.domain.recipe.support;

import com.naengo.api_server.domain.recipe.entity.Ingredient;
import com.naengo.api_server.domain.recipe.entity.RecipeIngredient;
import com.naengo.api_server.domain.recipe.entity.RecipeLabel;
import com.naengo.api_server.domain.recipe.entity.RecipeMedia;
import com.naengo.api_server.domain.recipe.entity.RecipeStep;

import java.util.ArrayList;
import java.util.List;

/**
 * 정규화 ↔ api-3.json 평면 계약 변환 (순수 함수).
 *
 * <p>읽기: 분리 테이블 row → 평면 필드(ingredients/instructions/category/…).
 * 쓰기(승격): 평면 입력 → recipe_ingredients/steps/labels/media row.
 * 평면 계약(클라이언트)은 변하지 않고, DB 만 정규화된다.
 */
public final class RecipeNormalizer {

    private RecipeNormalizer() {
    }

    // ─── 읽기 (정규화 → 평면) ─────────────────────────────

    public static List<Ingredient> toIngredientDtos(List<RecipeIngredient> rows) {
        List<Ingredient> out = new ArrayList<>();
        for (RecipeIngredient r : rows) {
            out.add(new Ingredient(
                    nz(r.getName()),
                    nz(r.getAmountText()),
                    nz(r.getUnit()),
                    nz(r.getGroupName()),
                    r.getNote()));
        }
        return out;
    }

    /** 평면 ingredients_raw 재구성. raw_text 우선, 없으면 "name amount unit". */
    public static String toIngredientsRaw(List<RecipeIngredient> rows) {
        if (rows.isEmpty()) return null;
        List<String> lines = new ArrayList<>();
        for (RecipeIngredient r : rows) {
            if (r.getRawText() != null && !r.getRawText().isBlank()) {
                lines.add(r.getRawText());
            } else {
                String line = (nz(r.getName()) + " " + nz(r.getAmountText()) + nz(r.getUnit())).trim();
                lines.add(line);
            }
        }
        return String.join("\n", lines);
    }

    public static List<String> toInstructions(List<RecipeStep> steps) {
        List<String> out = new ArrayList<>();
        for (RecipeStep s : steps) out.add(s.getInstruction());
        return out;
    }

    public static List<String> labelValues(List<RecipeLabel> labels, String type) {
        List<String> out = new ArrayList<>();
        for (RecipeLabel l : labels) {
            if (type.equals(l.getLabelType())) out.add(l.getLabelValue());
        }
        return out;
    }

    /** media 리스트는 isPrimary DESC 정렬 가정 — 첫 IMAGE storage_url. */
    public static String primaryImageUrl(List<RecipeMedia> media) {
        for (RecipeMedia m : media) {
            if (RecipeMedia.TYPE_IMAGE.equals(m.getMediaType())) return m.getStorageUrl();
        }
        return null;
    }

    public static String videoUrl(List<RecipeMedia> media) {
        for (RecipeMedia m : media) {
            if (RecipeMedia.TYPE_VIDEO.equals(m.getMediaType())) return m.getStorageUrl();
        }
        return null;
    }

    // ─── 쓰기 (평면 → 정규화) ─────────────────────────────

    public static List<RecipeIngredient> toIngredientRows(List<Ingredient> dtos) {
        List<RecipeIngredient> out = new ArrayList<>();
        if (dtos == null) return out;
        int i = 0;
        for (Ingredient d : dtos) {
            out.add(RecipeIngredient.builder()
                    .name(d.name())
                    .amountText(blankToNull(d.amount()))
                    .unit(blankToNull(d.unit()))
                    .groupName(blankToNull(d.type()))
                    .note(d.note())
                    .sortOrder(i++)
                    .build());
        }
        return out;
    }

    public static List<RecipeStep> toStepRows(List<String> instructions) {
        List<RecipeStep> out = new ArrayList<>();
        if (instructions == null) return out;
        int no = 1;
        for (String text : instructions) {
            out.add(RecipeStep.builder()
                    .stepNo(no)
                    .instruction(text)
                    .sortOrder(no - 1)
                    .build());
            no++;
        }
        return out;
    }

    public static List<RecipeLabel> toLabelRows(List<String> category,
                                                List<String> tags,
                                                List<String> tips) {
        List<RecipeLabel> out = new ArrayList<>();
        appendLabels(out, category, RecipeLabel.TYPE_CATEGORY);
        appendLabels(out, tags, RecipeLabel.TYPE_TAG);
        appendLabels(out, tips, RecipeLabel.TYPE_TIP);
        return out;
    }

    public static List<RecipeMedia> toMediaRows(String imageUrl, String videoUrl) {
        List<RecipeMedia> out = new ArrayList<>();
        if (imageUrl != null && !imageUrl.isBlank()) {
            out.add(RecipeMedia.builder()
                    .mediaType(RecipeMedia.TYPE_IMAGE)
                    .imageRole("MAIN")
                    .storageUrl(imageUrl)
                    .isPrimary(true)
                    .sortOrder(0)
                    .build());
        }
        if (videoUrl != null && !videoUrl.isBlank()) {
            out.add(RecipeMedia.builder()
                    .mediaType(RecipeMedia.TYPE_VIDEO)
                    .storageUrl(videoUrl)
                    .sortOrder(out.size())
                    .build());
        }
        return out;
    }

    // ─── 내부 ─────────────────────────────────────────────

    private static void appendLabels(List<RecipeLabel> out, List<String> values, String type) {
        if (values == null) return;
        int i = out.size();
        for (String v : values) {
            out.add(RecipeLabel.builder()
                    .labelType(type)
                    .labelValue(v)
                    .source("ADMIN")
                    .sortOrder(i++)
                    .build());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
