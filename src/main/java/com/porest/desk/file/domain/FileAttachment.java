package com.porest.desk.file.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.file.type.ReferenceType;
import com.porest.desk.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "file_attachment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FileAttachment extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_row_id", nullable = false)
    private User user;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "stored_name", nullable = false, length = 255)
    private String storedName;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 30)
    private ReferenceType referenceType;

    @Column(name = "reference_row_id")
    private Long referenceRowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static FileAttachment create(User user, String originalName, String storedName,
                                         String filePath, String contentType, Long fileSize,
                                         ReferenceType referenceType, Long referenceRowId) {
        FileAttachment file = new FileAttachment();
        file.user = user;
        file.originalName = originalName;
        file.storedName = storedName;
        file.filePath = filePath;
        file.contentType = contentType;
        file.fileSize = fileSize;
        file.referenceType = referenceType;
        file.referenceRowId = referenceRowId;
        file.isDeleted = YNType.N;
        return file;
    }

    public void updateReference(ReferenceType referenceType, Long referenceRowId) {
        this.referenceType = referenceType;
        this.referenceRowId = referenceRowId;
    }

    public void deleteFile() {
        this.isDeleted = YNType.Y;
    }
}
