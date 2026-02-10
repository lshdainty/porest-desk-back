package com.porest.desk.todo.service;

import com.porest.core.exception.EntityNotFoundException;
import com.porest.desk.common.exception.DeskErrorCode;
import com.porest.desk.todo.domain.TodoProject;
import com.porest.desk.todo.repository.TodoProjectRepository;
import com.porest.desk.todo.service.dto.TodoProjectServiceDto;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TodoProjectServiceImpl implements TodoProjectService {
    private final TodoProjectRepository todoProjectRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TodoProjectServiceDto.ProjectInfo createProject(TodoProjectServiceDto.CreateCommand command) {
        log.debug("프로젝트 등록 시작: userRowId={}, projectName={}", command.userRowId(), command.projectName());

        User user = userRepository.findById(command.userRowId())
            .orElseThrow(() -> new EntityNotFoundException(DeskErrorCode.USER_NOT_FOUND));

        TodoProject project = TodoProject.createProject(
            user, command.projectName(), command.description(), command.color(), command.icon()
        );

        todoProjectRepository.save(project);
        log.info("프로젝트 등록 완료: projectId={}", project.getRowId());

        return TodoProjectServiceDto.ProjectInfo.from(project);
    }

    @Override
    public List<TodoProjectServiceDto.ProjectInfo> getProjects(Long userRowId) {
        log.debug("프로젝트 목록 조회: userRowId={}", userRowId);

        return todoProjectRepository.findAllByUser(userRowId).stream()
            .map(TodoProjectServiceDto.ProjectInfo::from)
            .toList();
    }

    @Override
    @Transactional
    public TodoProjectServiceDto.ProjectInfo updateProject(Long projectId, TodoProjectServiceDto.UpdateCommand command) {
        log.debug("프로젝트 수정 시작: projectId={}", projectId);

        TodoProject project = findProjectOrThrow(projectId);
        project.updateProject(command.projectName(), command.description(), command.color(), command.icon());

        log.info("프로젝트 수정 완료: projectId={}", projectId);

        return TodoProjectServiceDto.ProjectInfo.from(project);
    }

    @Override
    @Transactional
    public void reorderProjects(Long userRowId, TodoProjectServiceDto.ReorderCommand command) {
        log.debug("프로젝트 순서 변경 시작: userRowId={}", userRowId);

        for (TodoProjectServiceDto.ReorderCommand.ReorderItem item : command.items()) {
            TodoProject project = findProjectOrThrow(item.projectId());
            project.updateSortOrder(item.sortOrder());
        }

        log.info("프로젝트 순서 변경 완료: userRowId={}", userRowId);
    }

    @Override
    @Transactional
    public void deleteProject(Long projectId) {
        log.debug("프로젝트 삭제 시작: projectId={}", projectId);

        TodoProject project = findProjectOrThrow(projectId);
        project.deleteProject();

        log.info("프로젝트 삭제 완료: projectId={}", projectId);
    }

    private TodoProject findProjectOrThrow(Long projectId) {
        return todoProjectRepository.findById(projectId)
            .orElseThrow(() -> {
                log.warn("프로젝트 조회 실패 - 존재하지 않는 프로젝트: projectId={}", projectId);
                return new EntityNotFoundException(DeskErrorCode.TODO_PROJECT_NOT_FOUND);
            });
    }
}
