package com.example.hackathon.service;

import com.example.hackathon.exception.ResourceNotFoundException;
import com.example.hackathon.model.User;
import com.example.hackathon.repository.UserRepository;
import com.example.hackathon.util.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    public UserService(UserRepository userRepository, MongoTemplate mongoTemplate) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public User getCurrentUser() {
        String email = SecurityUtil.currentUsername();
        if (email == null) {
            throw new ResourceNotFoundException("Authenticated user not found");
        }
        return findByEmail(email);
    }

    public User findByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        List<User> candidates = userRepository.findAllByEmailIgnoreCase(normalizedEmail);
        if (candidates.isEmpty()) {
            throw new ResourceNotFoundException("User not found for email: " + normalizedEmail);
        }
        if (candidates.size() > 1) {
            log.warn("Duplicate users found for email '{}'. Using the preferred record.", normalizedEmail);
        }
        return pickPreferredUser(candidates);
    }

    public String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public int deduplicateUsersByEmail() {
        List<User> users = userRepository.findAll();
        Map<String, List<User>> groupedByEmail = users.stream()
                .filter(user -> user.getEmail() != null && !user.getEmail().isBlank())
                .collect(Collectors.groupingBy(user -> normalizeEmail(user.getEmail())));

        int removed = 0;
        for (Map.Entry<String, List<User>> entry : groupedByEmail.entrySet()) {
            String normalizedEmail = entry.getKey();
            List<User> group = entry.getValue();
            if (group.isEmpty()) {
                continue;
            }

            User primary = pickPreferredUser(group);
            if (!normalizedEmail.equals(primary.getEmail())) {
                primary.setEmail(normalizedEmail);
                userRepository.save(primary);
            }

            for (User user : group) {
                if (!Objects.equals(user.getId(), primary.getId())) {
                    userRepository.deleteById(user.getId());
                    removed++;
                }
            }
        }

        ensureUniqueEmailIndex();
        if (removed > 0) {
            log.warn("Deduplicated {} duplicate user record(s).", removed);
        }
        return removed;
    }

    private User pickPreferredUser(List<User> users) {
        Comparator<User> comparator = Comparator
                .comparing(User::isActive, Comparator.nullsLast(Boolean::compareTo))
                .thenComparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(User::getId, Comparator.nullsLast(String::compareTo));

        return users.stream().max(comparator)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void ensureUniqueEmailIndex() {
        try {
            mongoTemplate.indexOps(User.class)
                    .ensureIndex(new Index().on("email", Sort.Direction.ASC).unique());
        } catch (Exception ex) {
            log.warn("Unable to enforce unique email index: {}", ex.getMessage());
        }
    }
}
