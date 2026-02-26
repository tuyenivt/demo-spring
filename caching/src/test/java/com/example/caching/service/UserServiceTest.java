package com.example.caching.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @InjectMocks
    private UserService userService;

    @Test
    void addUserActivity_shouldAddActivityAndSetTTL() {
        // Given
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.rightPush(any(), any())).thenReturn(1L);
        when(redisTemplate.expire(any(), any(Duration.class))).thenReturn(true);

        // When
        userService.addUserActivity("user123", "Logged in");

        // Then
        verify(listOperations).rightPush("user:activities:user123", "Logged in");
        verify(listOperations).trim("user:activities:user123", -100, -1);
        verify(redisTemplate).expire("user:activities:user123", Duration.ofDays(7));
    }

    @Test
    void addUserActivity_shouldThrowException_whenUserIdIsNull() {
        // When / Then
        assertThatThrownBy(() -> userService.addUserActivity(null, "activity"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be null or blank");
    }

    @Test
    void addUserActivity_shouldThrowException_whenUserIdIsBlank() {
        // When / Then
        assertThatThrownBy(() -> userService.addUserActivity("", "activity"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be null or blank");
    }

    @Test
    void addUserActivity_shouldThrowException_whenActivityIsNull() {
        // When / Then
        assertThatThrownBy(() -> userService.addUserActivity("user123", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("activity cannot be null or blank");
    }

    @Test
    void addUserActivity_shouldThrowException_whenActivityIsBlank() {
        // When / Then
        assertThatThrownBy(() -> userService.addUserActivity("user123", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("activity cannot be null or blank");
    }

    @Test
    void getOldestUserActivity_shouldReturnActivity() {
        // Given
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("user:activities:user123")).thenReturn("Logged in");

        // When
        String result = userService.getOldestUserActivity("user123");

        // Then
        assertThat(result).isEqualTo("Logged in");
        verify(listOperations).leftPop("user:activities:user123");
    }

    @Test
    void getOldestUserActivity_shouldReturnNull_whenNoActivity() {
        // Given
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.leftPop("user:activities:user123")).thenReturn(null);

        // When
        String result = userService.getOldestUserActivity("user123");

        // Then
        assertThat(result).isNull();
        verify(listOperations).leftPop("user:activities:user123");
    }

    @Test
    void getOldestUserActivity_shouldThrowException_whenUserIdIsNull() {
        // When / Then
        assertThatThrownBy(() -> userService.getOldestUserActivity(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be null or blank");
    }

    @Test
    void getOldestUserActivity_shouldThrowException_whenUserIdIsBlank() {
        // When / Then
        assertThatThrownBy(() -> userService.getOldestUserActivity(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be null or blank");
    }

    @Test
    void getUserActivities_shouldReturnActivities() {
        // Given
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("user:activities:user123", 0, -1))
                .thenReturn(List.of("Logged in", "Viewed dashboard"));

        // When
        List<String> result = userService.getUserActivities("user123");

        // Then
        assertThat(result).containsExactly("Logged in", "Viewed dashboard");
        verify(listOperations).range("user:activities:user123", 0, -1);
    }

    @Test
    void getUserActivities_shouldReturnEmptyList_whenNoActivity() {
        // Given
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("user:activities:user123", 0, -1)).thenReturn(null);

        // When
        List<String> result = userService.getUserActivities("user123");

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void getUserActivities_shouldThrowException_whenUserIdIsBlank() {
        // When / Then
        assertThatThrownBy(() -> userService.getUserActivities(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId cannot be null or blank");
    }
}
