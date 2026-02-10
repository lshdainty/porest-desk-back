package com.porest.desk.album.domain;

import com.porest.core.type.YNType;
import com.porest.desk.common.domain.AuditingFieldsWithIp;
import com.porest.desk.file.domain.FileAttachment;
import com.porest.desk.group.domain.UserGroup;
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
@Table(name = "album")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Album extends AuditingFieldsWithIp {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "row_id")
    private Long rowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_row_id", nullable = false)
    private UserGroup group;

    @Column(name = "album_name", nullable = false, length = 100)
    private String albumName;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_file_row_id")
    private FileAttachment coverFile;

    @Enumerated(EnumType.STRING)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private YNType isDeleted;

    public static Album createAlbum(UserGroup group, String albumName, String description) {
        Album album = new Album();
        album.group = group;
        album.albumName = albumName;
        album.description = description;
        album.isDeleted = YNType.N;
        return album;
    }

    public void updateAlbum(String albumName, String description) {
        this.albumName = albumName;
        this.description = description;
    }

    public void setCoverFile(FileAttachment coverFile) {
        this.coverFile = coverFile;
    }

    public void deleteAlbum() {
        this.isDeleted = YNType.Y;
    }
}
