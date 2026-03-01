package io.github.kamill7779.qforge.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("q_tag_category")
public class TagCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("category_code")
    private String categoryCode;

    @TableField("category_name")
    private String categoryName;

    @TableField("category_kind")
    private String categoryKind;

    @TableField("input_mode")
    private String inputMode;

    @TableField("allow_user_create")
    private boolean allowUserCreate;

    @TableField("sort_order")
    private int sortOrder;

    private boolean enabled;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getCategoryKind() {
        return categoryKind;
    }

    public void setCategoryKind(String categoryKind) {
        this.categoryKind = categoryKind;
    }

    public String getInputMode() {
        return inputMode;
    }

    public void setInputMode(String inputMode) {
        this.inputMode = inputMode;
    }

    public boolean isAllowUserCreate() {
        return allowUserCreate;
    }

    public void setAllowUserCreate(boolean allowUserCreate) {
        this.allowUserCreate = allowUserCreate;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

