package com.example.hackathon.service;

import com.example.hackathon.dto.CreateFormRequest;
import com.example.hackathon.dto.FormFieldRequest;
import com.example.hackathon.exception.BadRequestException;
import com.example.hackathon.exception.ResourceNotFoundException;
import com.example.hackathon.model.FieldType;
import com.example.hackathon.model.FormField;
import com.example.hackathon.model.RegistrationForm;
import com.example.hackathon.repository.RegistrationFormRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FormService {

    private static final long FORM_CACHE_TTL_MS = 120_000;

    private final RegistrationFormRepository formRepository;
    private final EventService eventService;
    private final ConcurrentHashMap<String, CachedFormEntry> formCacheByEventId = new ConcurrentHashMap<>();

    public FormService(RegistrationFormRepository formRepository, EventService eventService) {
        this.formRepository = formRepository;
        this.eventService = eventService;
    }

    public RegistrationForm createOrUpdateForm(CreateFormRequest request, String facultyEmail) {
        eventService.getEventEntity(request.eventId());
        RegistrationForm form = formRepository.findByEventId(request.eventId()).orElseGet(RegistrationForm::new);
        form.setEventId(request.eventId());
        form.setFields(request.fields().stream().map(this::toField).toList());
        form.setCreatedBy(facultyEmail);
        form.setUpdatedAt(Instant.now());
        RegistrationForm saved = formRepository.save(form);
        cacheForm(saved);
        return saved;
    }

    public RegistrationForm ensureDefaultFormForEvent(String eventId, String createdBy) {
        eventService.getEventEntity(eventId);
        RegistrationForm cached = getCachedForm(eventId);
        if (cached != null) {
            return cached;
        }

        RegistrationForm resolved = formRepository.findByEventId(eventId)
                .orElseGet(() -> createDefaultForm(eventId, createdBy));
        cacheForm(resolved);
        return resolved;
    }

    public RegistrationForm getOrCreateFormByEventId(String eventId) {
        return ensureDefaultFormForEvent(eventId, "SYSTEM_DEFAULT");
    }

    public RegistrationForm getFormByEventId(String eventId) {
        RegistrationForm cached = getCachedForm(eventId);
        if (cached != null) {
            return cached;
        }

        RegistrationForm resolved = formRepository.findByEventId(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration form not found for event: " + eventId));
        cacheForm(resolved);
        return resolved;
    }

    public void validateFormResponse(String eventId, Map<String, Object> formResponses) {
        RegistrationForm form = getOrCreateFormByEventId(eventId);
        for (FormField field : form.getFields()) {
            if (field.isRequired()) {
                Object value = formResponses.get(field.getKey());
                if (value == null || value.toString().isBlank()) {
                    throw new BadRequestException("Missing required field: " + field.getLabel());
                }
            }
        }
    }

    private FormField toField(FormFieldRequest request) {
        FormField field = new FormField();
        field.setKey(request.key());
        field.setLabel(request.label());
        field.setType(request.type());
        field.setRequired(request.required());
        field.setOptions(request.options());
        return field;
    }

    private RegistrationForm createDefaultForm(String eventId, String createdBy) {
        RegistrationForm form = new RegistrationForm();
        form.setEventId(eventId);
        form.setFields(defaultFields());
        form.setCreatedBy(createdBy == null || createdBy.isBlank() ? "SYSTEM_DEFAULT" : createdBy);
        form.setUpdatedAt(Instant.now());
        RegistrationForm saved = formRepository.save(form);
        cacheForm(saved);
        return saved;
    }

    private List<FormField> defaultFields() {
        List<FormField> fields = new ArrayList<>();
        fields.add(defaultField("teamIdea", "Team Idea", FieldType.TEXTAREA, true));
        fields.add(defaultField("projectDomain", "Project Domain", FieldType.TEXT, true));
        fields.add(defaultField("techStack", "Tech Stack", FieldType.TEXT, false));
        fields.add(defaultSelectField("accommodationRequired", "Accommodation Required", false, List.of("Yes", "No")));
        return fields;
    }

    private FormField defaultField(String key, String label, FieldType type, boolean required) {
        FormField field = new FormField();
        field.setKey(key);
        field.setLabel(label);
        field.setType(type);
        field.setRequired(required);
        field.setOptions(new ArrayList<>());
        return field;
    }

    private FormField defaultSelectField(String key, String label, boolean required, List<String> options) {
        FormField field = defaultField(key, label, FieldType.SELECT, required);
        field.setOptions(new ArrayList<>(options));
        return field;
    }

    private RegistrationForm getCachedForm(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        CachedFormEntry cached = formCacheByEventId.get(eventId);
        if (cached == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - cached.cachedAtMs() > FORM_CACHE_TTL_MS) {
            formCacheByEventId.remove(eventId);
            return null;
        }
        return cached.form();
    }

    private void cacheForm(RegistrationForm form) {
        if (form == null || form.getEventId() == null || form.getEventId().isBlank()) {
            return;
        }
        formCacheByEventId.put(form.getEventId(), new CachedFormEntry(form, System.currentTimeMillis()));
    }

    private record CachedFormEntry(RegistrationForm form, long cachedAtMs) {
    }
}
