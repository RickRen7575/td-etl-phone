package com.tirediscounters.etl.phone.model

class ReportHeader {
    public String ID
    public Integer Type
    public String Description
    public Boolean IsLicensed

    public ReportHeader() {
    }

    public ReportHeader(String id, Integer type, String desc, Boolean isLicensed) {
        this.ID = id
        this.Type = type
        this.Description = desc
        this.IsLicensed = isLicensed
    }
}