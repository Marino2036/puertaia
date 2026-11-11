package com.marino.puertaia.cloud;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final JdbcTemplate jdbc;
    private final String appKey;
    private final String deviceToken;

    public ApiController(JdbcTemplate jdbc,
                         @Value("${puertaia.app-key}") String appKey,
                         @Value("${puertaia.device-token}") String deviceToken) {
        this.jdbc = jdbc;
        this.appKey = appKey;
        this.deviceToken = deviceToken;
    }

    private void requireApp(String supplied) {
        if (supplied == null || !supplied.equals(appKey))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "app_key_invalid");
    }

    private void requireDevice(String supplied) {
        if (supplied == null || !supplied.equals(deviceToken))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "device_token_invalid");
    }

    @GetMapping("/health")
    public Map<String,Object> health() {
        return Map.of("ok", true, "service", "PuertaIA Cloud", "time", Instant.now().toString());
    }

    public record OpenRequest(String user, String method, Integer seconds) {}
    public record CompleteRequest(String status, String message) {}
    public record HeartbeatRequest(String ssid, String ip, String firmware) {}
    public record EventRequest(String type, String user, String method, String detail) {}
    public record DeviceCommandRequest(String action, String text, Integer number) {}
    public record FingerprintStateRequest(String stateJson) {}

    @PostMapping("/app/doors/{doorId}/open")
    public Map<String,Object> open(@RequestHeader(value="X-APP-KEY", required=false) String key,
                                   @PathVariable String doorId,
                                   @RequestBody(required=false) OpenRequest body) {
        requireApp(key);
        int seconds = body != null && body.seconds() != null ? Math.max(1, Math.min(30, body.seconds())) : 5;
        String user = body != null && body.user() != null && !body.user().isBlank() ? body.user().trim() : "Administrador";
        String method = body != null && body.method() != null && !body.method().isBlank() ? body.method().trim() : "APP_CLOUD";

        Long id = jdbc.queryForObject(
                "INSERT INTO door_commands(door_id,action,seconds,requested_by,method,status) VALUES (?,?,?,?,?,'PENDING') RETURNING id",
                Long.class, doorId, "OPEN", seconds, user, method);
        return Map.of("ok", true, "commandId", id, "status", "PENDING");
    }


    @PostMapping("/app/doors/{doorId}/command")
    public Map<String,Object> deviceCommand(@RequestHeader(value="X-APP-KEY", required=false) String key,
                                            @PathVariable String doorId,
                                            @RequestBody DeviceCommandRequest body) {
        requireApp(key);
        if (body == null || body.action() == null || body.action().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action_required");
        String action = body.action().trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = Set.of(
                "FINGERPRINT_LIST", "FINGERPRINT_ENROLL_START", "FINGERPRINT_ENROLL_STATUS",
                "FINGERPRINT_ENROLL_CANCEL", "FINGERPRINT_DELETE",
                "WIFI_LIST", "WIFI_DELETE"
        );
        if (!allowed.contains(action))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action_not_allowed");
        Long id = jdbc.queryForObject(
                "INSERT INTO door_commands(door_id,action,seconds,requested_by,method,status,param_text,param_int) VALUES (?,?,?,?,?,'PENDING',?,?) RETURNING id",
                Long.class, doorId, action, 5, "Administrador", "APP_CLOUD_MAINTENANCE",
                body.text(), body.number());
        return Map.of("ok", true, "commandId", id, "status", "PENDING");
    }

    @GetMapping("/app/commands/{id}")
    public Map<String,Object> commandStatus(@RequestHeader(value="X-APP-KEY", required=false) String key,
                                            @PathVariable long id) {
        requireApp(key);
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT id,door_id,action,seconds,requested_by,method,status,result_message,created_at,claimed_at,completed_at FROM door_commands WHERE id=?",
                id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "command_not_found");
        return normalize(rows.get(0));
    }

    @GetMapping("/app/doors/{doorId}/status")
    public Map<String,Object> doorStatus(@RequestHeader(value="X-APP-KEY", required=false) String key,
                                         @PathVariable String doorId) {
        requireApp(key);
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT * FROM device_state WHERE door_id=?", doorId);
        if (rows.isEmpty()) return Map.of("ok", true, "doorId", doorId, "online", false);
        Map<String,Object> out = normalize(rows.get(0));
        Timestamp ts = (Timestamp) rows.get(0).get("last_seen");
        boolean online = ts != null && ts.toInstant().isAfter(Instant.now().minusSeconds(10));
        out.put("ok", true);
        out.put("online", online);
        return out;
    }


    @GetMapping("/app/doors/{doorId}/fingerprint-state")
    public Map<String,Object> fingerprintState(@RequestHeader(value="X-APP-KEY", required=false) String key,
                                               @PathVariable String doorId) {
        requireApp(key);
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT fingerprint_state,fingerprint_state_updated_at FROM device_state WHERE door_id=?", doorId);
        if (rows.isEmpty()) {
            return Map.of("ok", true, "stateJson", "{\"state\":\"listo\",\"active\":false,\"step\":0,\"message\":\"Esperando al lector...\"}");
        }
        Object state = rows.get(0).get("fingerprint_state");
        Object updated = rows.get(0).get("fingerprint_state_updated_at");
        String stateJson = state == null ? "{\"state\":\"listo\",\"active\":false,\"step\":0,\"message\":\"Esperando al lector...\"}" : state.toString();
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("stateJson", stateJson);
        out.put("updatedAt", updated instanceof Timestamp t ? t.toInstant().toString() : updated);
        return out;
    }

    @PostMapping("/device/{doorId}/fingerprint-state")
    public Map<String,Object> updateFingerprintState(@RequestHeader(value="X-DEVICE-TOKEN", required=false) String token,
                                                     @PathVariable String doorId,
                                                     @RequestBody(required=false) FingerprintStateRequest body) {
        requireDevice(token);
        String stateJson = body == null || body.stateJson() == null ? "" : body.stateJson();
        jdbc.update("""
                INSERT INTO device_state(door_id,online,fingerprint_state,fingerprint_state_updated_at,last_seen)
                VALUES (?,TRUE,?,NOW(),NOW())
                ON CONFLICT (door_id) DO UPDATE SET online=TRUE,fingerprint_state=EXCLUDED.fingerprint_state,
                    fingerprint_state_updated_at=NOW(),last_seen=NOW()
                """, doorId, stateJson);
        return Map.of("ok", true);
    }

    @GetMapping("/device/{doorId}/commands/next")
    public Map<String,Object> next(@RequestHeader(value="X-DEVICE-TOKEN", required=false) String token,
                                   @PathVariable String doorId) {
        requireDevice(token);
        List<Map<String,Object>> rows = jdbc.queryForList(
                "SELECT id,action,seconds,requested_by,method,param_text,param_int FROM door_commands WHERE door_id=? AND status='PENDING' ORDER BY id LIMIT 1",
                doorId);
        if (rows.isEmpty()) return Map.of("ok", true, "hasCommand", false);
        Map<String,Object> row = rows.get(0);
        long id = ((Number)row.get("id")).longValue();
        int changed = jdbc.update("UPDATE door_commands SET status='CLAIMED', claimed_at=NOW() WHERE id=? AND status='PENDING'", id);
        if (changed == 0) return Map.of("ok", true, "hasCommand", false);
        Map<String,Object> out = normalize(row);
        out.put("ok", true);
        out.put("hasCommand", true);
        return out;
    }

    @PostMapping("/device/{doorId}/commands/{id}/complete")
    public Map<String,Object> complete(@RequestHeader(value="X-DEVICE-TOKEN", required=false) String token,
                                       @PathVariable String doorId,
                                       @PathVariable long id,
                                       @RequestBody(required=false) CompleteRequest body) {
        requireDevice(token);
        String status = body != null && "ERROR".equalsIgnoreCase(body.status()) ? "ERROR" : "COMPLETED";
        String message = body != null ? body.message() : null;
        int n = jdbc.update("UPDATE door_commands SET status=?, result_message=?, completed_at=NOW() WHERE id=? AND door_id=?",
                status, message, id, doorId);
        if (n == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "command_not_found");
        return Map.of("ok", true, "id", id, "status", status);
    }

    @PostMapping("/device/{doorId}/heartbeat")
    public Map<String,Object> heartbeat(@RequestHeader(value="X-DEVICE-TOKEN", required=false) String token,
                                        @PathVariable String doorId,
                                        @RequestBody(required=false) HeartbeatRequest body) {
        requireDevice(token);
        String ssid = body == null ? null : body.ssid();
        String ip = body == null ? null : body.ip();
        String fw = body == null ? null : body.firmware();
        jdbc.update("""
                INSERT INTO device_state(door_id,online,wifi_ssid,ip_local,firmware,last_seen)
                VALUES (?,TRUE,?,?,?,NOW())
                ON CONFLICT (door_id) DO UPDATE SET online=TRUE,wifi_ssid=EXCLUDED.wifi_ssid,ip_local=EXCLUDED.ip_local,firmware=EXCLUDED.firmware,last_seen=NOW()
                """, doorId, ssid, ip, fw);
        return Map.of("ok", true);
    }

    @PostMapping("/device/{doorId}/events")
    public Map<String,Object> event(@RequestHeader(value="X-DEVICE-TOKEN", required=false) String token,
                                    @PathVariable String doorId,
                                    @RequestBody EventRequest body) {
        requireDevice(token);
        jdbc.update("INSERT INTO access_events(door_id,event_type,username,method,detail) VALUES (?,?,?,?,?)",
                doorId, body.type(), body.user(), body.method(), body.detail());
        return Map.of("ok", true);
    }

    private Map<String,Object> normalize(Map<String,Object> src) {
        Map<String,Object> out = new LinkedHashMap<>();
        for (var e : src.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Timestamp t) v = t.toInstant().toString();
            out.put(toCamel(e.getKey()), v);
        }
        return out;
    }

    private String toCamel(String s) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (char c : s.toCharArray()) {
            if (c == '_') { up = true; continue; }
            b.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return b.toString();
    }
}
