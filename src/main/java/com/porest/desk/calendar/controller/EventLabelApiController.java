package com.porest.desk.calendar.controller;

import com.porest.core.controller.ApiResponse;
import com.porest.desk.calendar.controller.dto.EventLabelApiDto;
import com.porest.desk.calendar.service.EventLabelService;
import com.porest.desk.calendar.service.dto.EventLabelServiceDto;
import com.porest.desk.security.annotation.LoginUser;
import com.porest.desk.security.principal.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventLabelApiController {
    private final EventLabelService eventLabelService;

    @PostMapping("/calendar/label")
    public ApiResponse<EventLabelApiDto.Response> createLabel(
            @LoginUser UserPrincipal loginUser,
            @RequestBody EventLabelApiDto.CreateRequest request) {
        EventLabelServiceDto.LabelInfo info = eventLabelService.createLabel(new EventLabelServiceDto.CreateCommand(
            loginUser.getRowId(),
            request.labelName(),
            request.color()
        ));
        return ApiResponse.success(EventLabelApiDto.Response.from(info));
    }

    @GetMapping("/calendar/labels")
    public ApiResponse<EventLabelApiDto.ListResponse> getLabels(
            @LoginUser UserPrincipal loginUser) {
        List<EventLabelServiceDto.LabelInfo> infos = eventLabelService.getLabels(loginUser.getRowId());
        return ApiResponse.success(EventLabelApiDto.ListResponse.from(infos));
    }

    @PutMapping("/calendar/label/{id}")
    public ApiResponse<EventLabelApiDto.Response> updateLabel(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id,
            @RequestBody EventLabelApiDto.UpdateRequest request) {
        EventLabelServiceDto.LabelInfo info = eventLabelService.updateLabel(id, new EventLabelServiceDto.UpdateCommand(
            request.labelName(),
            request.color()
        ));
        return ApiResponse.success(EventLabelApiDto.Response.from(info));
    }

    @DeleteMapping("/calendar/label/{id}")
    public ApiResponse<Void> deleteLabel(
            @LoginUser UserPrincipal loginUser,
            @PathVariable Long id) {
        eventLabelService.deleteLabel(id);
        return ApiResponse.success();
    }
}
