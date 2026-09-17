package Timeout.travel_tackle.preference.service;
import Timeout.travel_tackle.preference.repository.UserPreferenceRepository;

import Timeout.travel_tackle.auth.repository.UserRepository;
import Timeout.travel_tackle.entity.User;
import Timeout.travel_tackle.entity.UserPreference;
import Timeout.travel_tackle.global.exception.CustomException;
import Timeout.travel_tackle.global.exception.ErrorCode;
import Timeout.travel_tackle.global.util.UuidConverter;
import Timeout.travel_tackle.preference.dto.PreferenceRequest;
import Timeout.travel_tackle.preference.dto.PreferenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceRepository userPreferenceRepository;
    private final UserRepository userRepository;

    @Transactional
    public PreferenceResponse create(String subject, PreferenceRequest request) {
        UUID userId = UuidConverter.fromSubject(subject);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.UNAUTHENTICATED));
        try {
            UserPreference preference = userPreferenceRepository.saveAndFlush(
                    new UserPreference(user, request.travelStyle(), request.budgetLevel(),
                            request.interestTags(), request.preferredRegions())
            );
            return PreferenceResponse.from(preference);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.PREFERENCE_ALREADY_EXISTS);
        }
    }

    @Transactional(readOnly = true)
    public PreferenceResponse get(String subject) {
        UUID userId = UuidConverter.fromSubject(subject);
        UserPreference preference = userPreferenceRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PREFERENCE_NOT_FOUND));
        return PreferenceResponse.from(preference);
    }

    @Transactional
    public PreferenceResponse update(String subject, PreferenceRequest request) {
        UUID userId = UuidConverter.fromSubject(subject);
        UserPreference preference = userPreferenceRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.PREFERENCE_NOT_FOUND));
        preference.update(request.travelStyle(), request.budgetLevel(),
                request.interestTags(), request.preferredRegions());
        return PreferenceResponse.from(preference);
    }
}
