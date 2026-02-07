package com.desitech.vyaparsathi.changelog.mapper;

import com.desitech.vyaparsathi.changelog.dto.ChangeLogDto;
import com.desitech.vyaparsathi.changelog.entity.ChangeLog;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChangeLogMapper {
    ChangeLog toEntity(ChangeLogDto dto);
    ChangeLogDto toDto(ChangeLog entity);
    java.util.List<ChangeLogDto> toDtoList(java.util.List<ChangeLog> entities);
}