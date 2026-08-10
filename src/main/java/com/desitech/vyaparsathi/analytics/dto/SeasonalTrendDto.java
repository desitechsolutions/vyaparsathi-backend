package com.desitech.vyaparsathi.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeasonalTrendDto {
    private String season;
    private String trendDescription;

    public String getSeason() { return season; }
    public void setSeason(String season) { this.season = season; }

    public String getTrendDescription() { return trendDescription; }
    public void setTrendDescription(String trendDescription) { this.trendDescription = trendDescription; }
}
