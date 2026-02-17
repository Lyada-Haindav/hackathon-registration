package com.example.hackathon.model;

import jakarta.validation.constraints.NotBlank;

import java.util.ArrayList;
import java.util.List;

public class FormField {

    @NotBlank
    private String key;

    @NotBlank
    private String label;

    private FieldType type = FieldType.TEXT;

    private boolean required = true;

    private List<String> options = new ArrayList<>();

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public FieldType getType() {
        return type;
    }

    public void setType(FieldType type) {
        this.type = type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }
}
