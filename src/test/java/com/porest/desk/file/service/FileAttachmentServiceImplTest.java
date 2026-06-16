package com.porest.desk.file.service;

import com.porest.core.exception.ForbiddenException;
import com.porest.desk.file.domain.FileAttachment;
import com.porest.desk.file.repository.FileAttachmentRepository;
import com.porest.desk.file.service.dto.FileServiceDto;
import com.porest.desk.file.type.ReferenceType;
import com.porest.desk.user.domain.User;
import com.porest.desk.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * 파일 첨부 서비스 소유권 가드 회귀 방지 단위 테스트 — 남의 파일은 조회·삭제 불가.
 */
@ExtendWith(MockitoExtension.class)
class FileAttachmentServiceImplTest {

    @Mock private FileAttachmentRepository fileAttachmentRepository;
    @Mock private FileStorageService fileStorageService;
    @Mock private UserRepository userRepository;

    @InjectMocks private FileAttachmentServiceImpl sut;

    private static final long USER_ID = 1L;

    private User user(long rowId) {
        User u = User.createUser(null, "tester", "테스터", "tester@porest.com");
        ReflectionTestUtils.setField(u, "rowId", rowId);
        return u;
    }

    private FileAttachment othersFile() {
        FileAttachment f = mock(FileAttachment.class);
        given(f.getUser()).willReturn(user(999L));
        return f;
    }

    @Test
    @DisplayName("getFile — 남의 파일은 조회 불가")
    void getRejectsOthers() {
        FileAttachment f = othersFile();
        given(fileAttachmentRepository.findById(5L)).willReturn(Optional.of(f));

        assertThatThrownBy(() -> sut.getFile(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("deleteFile — 남의 파일은 삭제 불가")
    void deleteRejectsOthers() {
        FileAttachment f = othersFile();
        given(fileAttachmentRepository.findById(5L)).willReturn(Optional.of(f));

        assertThatThrownBy(() -> sut.deleteFile(5L, USER_ID))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("getFilesByReference — 본인 첨부만 반환하고 남의 첨부는 제외(읽기 인가 누락 보강)")
    void getFilesByReferenceReturnsOnlyOwn() {
        FileAttachment mine = mock(FileAttachment.class);
        given(mine.getUser()).willReturn(user(USER_ID));
        given(mine.getReferenceType()).willReturn(ReferenceType.EXPENSE_RECEIPT);
        FileAttachment others = othersFile();
        given(fileAttachmentRepository.findByReference(ReferenceType.EXPENSE_RECEIPT, 7L))
                .willReturn(List.of(mine, others));

        List<FileServiceDto.FileInfo> result =
                sut.getFilesByReference(ReferenceType.EXPENSE_RECEIPT, 7L, USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userRowId()).isEqualTo(USER_ID);
    }
}
