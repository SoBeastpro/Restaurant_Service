package com.restaurant.restaurant.setup.application;

import static com.restaurant.restaurant.setup.api.SetupDtos.CreateHallRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.CreateRestaurantRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.CreateTableRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.HallResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.HallLayout;
import static com.restaurant.restaurant.setup.api.SetupDtos.HallLayoutItem;
import static com.restaurant.restaurant.setup.api.SetupDtos.HallLayoutResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.HallTableUpdate;
import static com.restaurant.restaurant.setup.api.SetupDtos.OpeningHoursRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.OpeningHoursResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.RestaurantResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.SaveHallLayoutRequest;
import static com.restaurant.restaurant.setup.api.SetupDtos.TableResponse;
import static com.restaurant.restaurant.setup.api.SetupDtos.TokenPair;
import static com.restaurant.restaurant.setup.api.SetupDtos.UpdateTableRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.restaurant.foundation.web.ApiException;
import com.restaurant.restaurant.setup.infrastructure.IdentityOnboardingClient;
import com.restaurant.restaurant.setup.infrastructure.IdentityOnboardingClient.DirectorProfile;
import com.restaurant.restaurant.setup.infrastructure.RestaurantSetupRepository;
import com.restaurant.restaurant.setup.infrastructure.RestaurantSetupRepository.HallRecord;
import com.restaurant.restaurant.setup.infrastructure.RestaurantSetupRepository.RestaurantRecord;
import com.restaurant.restaurant.setup.infrastructure.RestaurantSetupRepository.TableRecord;
import com.restaurant.restaurant.setup.infrastructure.WorkforceOnboardingClient;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final ObjectMapper objectMapper;

    public RestaurantSetupService(
            RestaurantSetupRepository repository,
            IdentityOnboardingClient identityClient,
            WorkforceOnboardingClient workforceClient,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.identityClient = identityClient;
        this.workforceClient = workforceClient;
        this.objectMapper = objectMapper;
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

        TokenPair tokens;
        try {
            DirectorProfile director = identityClient.assignRestaurant(directorUserId, restaurantId);
            if (director == null || director.userId() == null || director.tokens() == null) {
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
            tokens = new TokenPair(
                    director.tokens().accessToken(),
                    director.tokens().refreshToken(),
                    director.tokens().tokenType(),
                    director.tokens().expiresIn());
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
        RestaurantResponse restaurant = getRestaurant(restaurantId, directorUserId, "DIRECTOR");
        return new RestaurantResponse(
                restaurant.id(),
                restaurant.name(),
                restaurant.address(),
                restaurant.status(),
                restaurant.openingHours(),
                restaurant.createdAt(),
                tokens);
    }

    @Transactional(readOnly = true)
    public RestaurantResponse getRestaurant(UUID restaurantId, UUID userId, String role) {
        RestaurantRecord restaurant = requireReader(restaurantId, userId, role);
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
                restaurant.createdAt(),
                null);
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
        requireReader(restaurantId, userId, role);
        return repository.findHalls(restaurantId).stream().map(this::hall).toList();
    }

    @Transactional(readOnly = true)
    public HallResponse getHall(UUID restaurantId, UUID hallId, UUID userId, String role) {
        requireReader(restaurantId, userId, role);
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
        requireReader(restaurantId, userId, role);
        List<TableRecord> tables = "EMPLOYEE".equals(role)
                ? repository.findEnabledTables(restaurantId)
                : repository.findTables(restaurantId);
        return tables.stream().map(RestaurantSetupService::table).toList();
    }

    @Transactional(readOnly = true)
    public TableResponse getTable(UUID restaurantId, UUID tableId, UUID userId, String role) {
        requireReader(restaurantId, userId, role);
        TableRecord table = repository.findTable(restaurantId, tableId)
                .filter(value -> !"EMPLOYEE".equals(role) || value.enabled())
                .orElseThrow(RestaurantSetupService::notFound);
        return table(table);
    }

    @Transactional
    public HallLayoutResponse saveHallLayout(
            UUID restaurantId,
            UUID hallId,
            UUID userId,
            String role,
            SaveHallLayoutRequest request) {
        requireEditor(restaurantId, userId, role);
        HallRecord hall = repository.findHall(restaurantId, hallId)
                .orElseThrow(RestaurantSetupService::notFound);
        HallLayout normalizedLayout = normalizeLayout(request.layout());

        List<UUID> requestedIds = request.tables().stream().map(HallTableUpdate::id).toList();
        if (new LinkedHashSet<>(requestedIds).size() != requestedIds.size()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "DUPLICATE_LAYOUT_TABLE",
                    "Один стол нельзя передать в схеме зала дважды.");
        }
        Map<UUID, TableRecord> currentTables = repository.findTablesByHall(restaurantId, hallId).stream()
                .collect(Collectors.toMap(TableRecord::id, Function.identity()));
        if (!currentTables.keySet().containsAll(requestedIds)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_LAYOUT_TABLE",
                    "В схеме можно изменять только существующие столы выбранного зала.");
        }

        try {
            repository.updateHallLayout(restaurantId, hallId, objectMapper.writeValueAsString(normalizedLayout));
            for (HallTableUpdate change : request.tables()) {
                TableRecord current = currentTables.get(change.id());
                repository.updateTable(new TableRecord(
                        current.id(),
                        current.restaurantId(),
                        current.hallId(),
                        change.number() == null ? current.number() : clean(change.number()),
                        change.seatsCount() == null ? current.seatsCount() : change.seatsCount(),
                        change.positionX() == null ? current.positionX() : change.positionX(),
                        change.positionY() == null ? current.positionY() : change.positionY(),
                        change.shape() == null ? current.shape() : normalizeShape(change.shape()),
                        change.status() == null ? current.status() : normalizeTableStatus(change.status()),
                        change.rotationDegrees() == null
                                ? current.rotationDegrees()
                                : normalizeRotation(change.rotationDegrees()),
                        change.enabled() == null ? current.enabled() : change.enabled()));
            }
        } catch (JacksonException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_HALL_LAYOUT",
                    "Не удалось сохранить схему зала.");
        } catch (DuplicateKeyException exception) {
            throw conflict("TABLE_NUMBER_ALREADY_EXISTS", "В этом зале уже есть стол с таким номером.");
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TABLE_STATE",
                    "Параметры стола противоречат правилам ресторана.");
        }
        HallRecord updatedHall = repository.findHall(restaurantId, hall.id())
                .orElseThrow(RestaurantSetupService::notFound);
        return new HallLayoutResponse(
                hall(updatedHall),
                repository.findTablesByHall(restaurantId, hallId).stream()
                        .map(RestaurantSetupService::table)
                        .toList());
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
                current.status(),
                current.rotationDegrees(),
                current.enabled());
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

    private RestaurantRecord requireReader(UUID restaurantId, UUID userId, String role) {
        RestaurantRecord restaurant = repository.findRestaurant(restaurantId)
                .orElseThrow(RestaurantSetupService::notFound);
        if ("DIRECTOR".equals(role)) {
            if (restaurant.directorUserId().equals(userId)) {
                return restaurant;
            }
        } else if (List.of("MANAGER", "EMPLOYEE").contains(role)) {
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

    private static String normalizeTableStatus(String value) {
        String status = clean(value).toUpperCase(Locale.ROOT);
        if (!List.of("READY", "NEEDS_CLEANING", "NEEDS_SERVING", "PROBLEM").contains(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TABLE_STATUS",
                    "Неизвестный статус стола.");
        }
        return status;
    }

    private static int normalizeRotation(int value) {
        if (value != 0 && value != 90) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_TABLE_ROTATION",
                    "Поворот стола должен быть 0 или 90 градусов.");
        }
        return value;
    }

    private static HallLayout normalizeLayout(HallLayout layout) {
        List<HallLayoutItem> normalizedItems = new java.util.ArrayList<>();
        for (HallLayoutItem item : layout.items()) {
            String type = clean(item.type()).toUpperCase(Locale.ROOT);
            if (!List.of("BAR", "WINDOW", "ENTRANCE", "WALL", "COLUMN", "PLANT").contains(type)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_LAYOUT_ITEM_TYPE",
                        "Неизвестный тип элемента схемы зала.");
            }
            normalizedItems.add(new HallLayoutItem(
                    type,
                    item.positionX(),
                    item.positionY(),
                    item.width(),
                    item.height()));
        }
        return new HallLayout(layout.canvasWidth(), layout.canvasHeight(), List.copyOf(normalizedItems));
    }

    private static String clean(String value) {
        String cleaned = value.strip();
        if (cleaned.isEmpty()) {
            throw validation("Значение не может быть пустым.");
        }
        return cleaned;
    }

    private HallResponse hall(HallRecord hall) {
        return new HallResponse(
                hall.id(),
                hall.restaurantId(),
                hall.name(),
                hall.displayOrder(),
                hall.status(),
                layout(hall.layoutJson()));
    }

    private HallLayout layout(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.strip())) {
            return new HallLayout(1200, 800, List.of());
        }
        try {
            HallLayout layout = objectMapper.readValue(json, HallLayout.class);
            if (layout.canvasWidth() < 1 || layout.canvasHeight() < 1 || layout.items() == null) {
                return new HallLayout(1200, 800, List.of());
            }
            return layout;
        } catch (JacksonException exception) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "INVALID_STORED_HALL_LAYOUT",
                    "Сохранённая схема зала повреждена.");
        }
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
                table.status(),
                table.rotationDegrees(),
                table.enabled());
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
