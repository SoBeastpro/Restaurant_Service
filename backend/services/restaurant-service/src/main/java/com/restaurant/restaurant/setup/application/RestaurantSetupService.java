package com.restaurant.restaurant.setup.application;

import static com.restaurant.restaurant.setup.api.SetupDtos.CreateHallRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.CreateRestaurantRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.CreateTableRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.HallResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.OpeningHoursRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.OpeningHoursResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.RestaurantResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.TableResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.UpdateTableRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.restaurant.foundation.web.ApiException;
import com.restaurant.restaurant.setup.infrastructure.IdentityOnboardingClient;
import com.restaurant.restaurant.setup.infrastructure.IdentityOnboardingClient.DirectorProfile;
import com.restaurant.restaurant.setup.infrastructure.RestaurantSetupRepository;
import com.restaurant.restaurant.setup.infrastructure.RestaurantSetupRepository.HallRecord;
import com.restaurant.restaurant.setup.infrastructure.RestaurantSetupRepository.RestaurantRecord;
import com.restaurant.restaurant.setup.infrastructure.RestaurantSetupRepository.TableRecord;
import com.restaurant.restaurant.setup.infrastructure.WorkforceOnboardingClient;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;

@Service
public class RestaurantSetupService {

    private final RestaurantSetupRepository repository;
    private final IdentityOnboardingClient identityClient;
    private final WorkforceOnboardingClient workforceClient;

    public RestaurantSetupService(
            RestaurantSetupRepository repository,
            IdentityOnboardingClient identityClient,
            WorkforceOnboardingClient workforceClient) {
        this.repository = repository;
        this.identityClient = identityClient;
        this.workforceClient = workforceClient;
    }

    @Transactional
    public RestaurantResponse createRestaurant(UUID directorUserId, CreateRestaurantRequest request) {
        if (repository.hasActiveRestaurant(directorUserId)) {
            throw conflict("RESTAURANT_ALREADY_EXISTS", "У директора уже есть ресторан.");
        }
        validateSchedule(request.openingHours());
        UUID restaurantId = UUID.nameUUIDFromBytes(
                ("restaurant:" + directorUserId).getBytes(StandardCharsets.UTF_8));
        repository.insertRestaurant(
                restaurantId,
                directorUserId,
                clean(request.name()),
                clean(request.address()));
        request.openingHours().stream()
                .sorted(java.util.Comparator.comparing(OpeningHoursRequest::dayOfWeek))
                .forEach(day -> repository.insertOpeningHours(
                        restaurantId,
                        day.dayOfWeek(),
                        day.closed(),
                        day.closed() ? null : day.opensAt(),
                        day.closed() ? null : day.closesAt(),
                        !day.closed() && day.closesAt().isBefore(day.opensAt())));

        try {
            DirectorProfile director = identityClient.assignRestaurant(directorUserId, restaurantId);
            if (director == null || director.userId() == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "IDENTITY_SERVICE_INVALID_RESPONSE",
                        "Сервис аккаунтов вернул некорректный ответ.");
            }
            workforceClient.createDirector(
                    restaurantId,
                    director.userId(),
                    director.firstName(),
                    director.lastName());
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409) {
                throw conflict("ONBOARDING_STATE_CONFLICT", "Не удалось связать ресторан с директором.");
            }
            if (exception.getStatusCode().value() == 403) {
                throw new ApiException(
                        HttpStatus.FORBIDDEN,
                        "ACTIVE_SUBSCRIPTION_REQUIRED",
                        "Сначала активируйте подписку.");
            }
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ONBOARDING_SERVICE_UNAVAILABLE",
                    "Не удалось завершить создание ресторана.");
        } catch (RestClientException exception) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "ONBOARDING_SERVICE_UNAVAILABLE",
                    "Не удалось завершить создание ресторана.");
        }
        return getRestaurant(restaurantId, directorUserId, "DIRECTOR");
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurant(UUID restaurantId, UUID userId, String role) {
        RestaurantRecord restaurant = requireEditor(restaurantId, userId, role);
        List<OpeningHoursResponse> hours = repository.findOpeningHours(restaurantId).stream()
                .map(day -> new OpeningHoursResponse(
                        day.dayOfWeek(),
                        day.closed(),
                        day.opensAt(),
                        day.closesAt(),
                        day.closesNextDay()))
                .toList();
        return new RestaurantResponse(
                restaurant.id(),
                restaurant.name(),
                restaurant.address(),
                restaurant.status(),
                hours,
                restaurant.createdAt());
    }

    @Transactional
    public HallResponse createHall(
            UUID restaurantId,
            UUID userId,
            String role,
            CreateHallRequest request) {
        requireEditor(restaurantId, userId, role);
        UUID hallId = UUID.randomUUID();
        try {
            repository.insertHall(
                    hallId,
                    restaurantId,
                    clean(request.name()),
                    request.displayOrder() == null ? 0 : request.displayOrder());
        } catch (DuplicateKeyException exception) {
            throw conflict("HALL_ALREADY_EXISTS", "Зал с таким названием уже существует.");
        }
        return hall(repository.findHall(restaurantId, hallId).orElseThrow(RestaurantSetupService::notFound));
    }

    @Transactional(readOnly = true)
    public List<HallResponse> listHalls(UUID restaurantId, UUID userId, String role) {
        requireEditor(restaurantId, userId, role);
        return repository.findHalls(restaurantId).stream().map(RestaurantSetupService::hall).toList();
    }

    @Transactional(readOnly = true)
    public HallResponse getHall(UUID restaurantId, UUID hallId, UUID userId, String role) {
        requireEditor(restaurantId, userId, role);
        return hall(repository.findHall(restaurantId, hallId).orElseThrow(RestaurantSetupService::notFound));
    }

    @Transactional
    public TableResponse createTable(
            UUID restaurantId,
            UUID hallId,
            UUID userId,
            String role,
            CreateTableRequest request) {
        requireEditor(restaurantId, userId, role);
        repository.findHall(restaurantId, hallId).orElseThrow(RestaurantSetupService::notFound);
        UUID tableId = UUID.randomUUID();
        try {
            repository.insertTable(
                    tableId,
                    restaurantId,
                    hallId,
                    clean(request.number()),
                    request.seatsCount(),
                    request.positionX(),
                    request.positionY(),
                    normalizeShape(request.shape()));
        } catch (DuplicateKeyException exception) {
            throw conflict("TABLE_NUMBER_ALREADY_EXISTS", "В этом зале уже есть стол с таким номером.");
        }
        return table(repository.findTable(restaurantId, tableId).orElseThrow(RestaurantSetupService::notFound));
    }

    @Transactional(readOnly = true)
    public List<TableResponse> listTables(UUID restaurantId, UUID userId, String role) {
        requireEditor(restaurantId, userId, role);
        return repository.findTables(restaurantId).stream().map(RestaurantSetupService::table).toList();
    }

    @Transactional(readOnly = true)
    public TableResponse getTable(UUID restaurantId, UUID tableId, UUID userId, String role) {
        requireEditor(restaurantId, userId, role);
        return table(repository.findTable(restaurantId, tableId).orElseThrow(RestaurantSetupService::notFound));
    }

    @Transactional
    public TableResponse updateTable(
            UUID restaurantId,
            UUID tableId,
            UUID userId,
            String role,
            UpdateTableRequest request) {
        requireEditor(restaurantId, userId, role);
        TableRecord current = repository.findTable(restaurantId, tableId)
                .orElseThrow(RestaurantSetupService::notFound);
        TableRecord updated = new TableRecord(
                current.id(),
                current.restaurantId(),
                current.hallId(),
                request.number() == null ? current.number() : clean(request.number()),
                request.seatsCount() == null ? current.seatsCount() : request.seatsCount(),
                request.positionX() == null ? current.positionX() : request.positionX(),
                request.positionY() == null ? current.positionY() : request.positionY(),
                request.shape() == null ? current.shape() : normalizeShape(request.shape()),
                current.status());
        try {
            repository.updateTable(updated);
        } catch (DuplicateKeyException exception) {
            throw conflict("TABLE_NUMBER_ALREADY_EXISTS", "В этом зале уже есть стол с таким номером.");
        }
        return table(updated);
    }

    private RestaurantRecord requireEditor(UUID restaurantId, UUID userId, String role) {
        RestaurantRecord restaurant = repository.findRestaurant(restaurantId)
                .orElseThrow(RestaurantSetupService::notFound);
        if ("DIRECTOR".equals(role) && restaurant.directorUserId().equals(userId)) {
            return restaurant;
        }
        if ("MANAGER".equals(role)) {
            return restaurant;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "RESTAURANT_ACCESS_REQUIRED", "Нет доступа к ресторану.");
    }

    private static void validateSchedule(List<OpeningHoursRequest> days) {
        EnumSet<DayOfWeek> seen = EnumSet.noneOf(DayOfWeek.class);
        for (OpeningHoursRequest day : days) {
            if (!seen.add(day.dayOfWeek())) {
                throw validation("Каждый день недели должен быть указан один раз.");
            }
            if (day.closed()) {
                if (day.opensAt() != null || day.closesAt() != null) {
                    throw validation("Для выходного дня время работы не указывается.");
                }
            } else if (day.opensAt() == null || day.closesAt() == null) {
                throw validation("Для рабочего дня укажите время открытия и закрытия.");
            } else if (day.opensAt().equals(day.closesAt())) {
                throw validation("Время открытия и закрытия не должно совпадать.");
            }
        }
        if (seen.size() != DayOfWeek.values().length) {
            throw validation("Нужно указать все семь дней недели.");
        }
    }

    private static String normalizeShape(String value) {
        String shape = clean(value).toUpperCase(Locale.ROOT);
        if (!List.of("ROUND", "SQUARE", "RECTANGLE").contains(shape)) {
            throw validation("Форма стола должна быть ROUND, SQUARE или RECTANGLE.");
        }
        return shape;
    }

    private static String clean(String value) {
        String cleaned = value.strip();
        if (cleaned.isEmpty()) {
            throw validation("Значение не может быть пустым.");
        }
        return cleaned;
    }

    private static HallResponse hall(HallRecord hall) {
        return new HallResponse(hall.id(), hall.restaurantId(), hall.name(), hall.displayOrder(), hall.status());
    }

    private static TableResponse table(TableRecord table) {
        return new TableResponse(
                table.id(),
                table.restaurantId(),
                table.hallId(),
                table.number(),
                table.seatsCount(),
                table.positionX(),
                table.positionY(),
                table.shape(),
                table.status());
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "RESTAURANT_RESOURCE_NOT_FOUND", "Объект не найден.");
    }

    private static ApiException validation(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }
}
