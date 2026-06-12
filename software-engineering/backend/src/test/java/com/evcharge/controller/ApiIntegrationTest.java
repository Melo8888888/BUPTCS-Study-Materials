package com.evcharge.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:api-integration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "evcharge.auth.enabled=false"
})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void vehicleRequestChangeEndPayAndReportFlow() throws Exception {
        mvc.perform(post("/api/admin/clock/reset").param("time", "06:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mvc.perform(post("/api/admin/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"waitingCapacity\":12,\"pileSlotCapacity\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stationConfig.waitingCapacity").value(12))
                .andExpect(jsonPath("$.data.stationConfig.pileSlotCapacity").value(4));

        mvc.perform(post("/api/vehicles/VTEST/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"F\",\"amountKwh\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.queueNo").value("F1"))
                .andExpect(jsonPath("$.data.status").value("CHARGING"))
                .andExpect(jsonPath("$.data.pileId").value("F1"));

        // 充电中改请求应被拒（PDF 第 6 条要求"不允许在充电区修改"）
        mvc.perform(post("/api/vehicles/VTEST/requests/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"F\",\"amountKwh\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("充电中")));

        mvc.perform(post("/api/admin/clock/advance").param("minutes", "20"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/vehicles/VTEST/charging/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mvc.perform(get("/api/vehicles/VTEST/bills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].chargedKwh").value(10.00))
                .andExpect(jsonPath("$.data[0].totalFee").value(12.00))
                .andExpect(jsonPath("$.data[0].paid").value(false));

        mvc.perform(post("/api/vehicles/VTEST/bills/pay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount").value(12.00))
                .andExpect(jsonPath("$.data.paidBillCount").value(1))
                .andExpect(jsonPath("$.data.bills[0].paid").value(true));

        mvc.perform(get("/api/admin/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestCount").value(1))
                .andExpect(jsonPath("$.data.detailCount").value(1))
                .andExpect(jsonPath("$.data.paidCount").value(1))
                .andExpect(jsonPath("$.data.totalFee").value(12.00));
    }

    @Test
    void defaultAcceptanceEndpointProducesFaultsAndBills() throws Exception {
        mvc.perform(post("/api/acceptance/run-default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.now").value("2026-05-12T10:50:00"))
                .andExpect(jsonPath("$.data.details.length()").value(13));

        mvc.perform(get("/api/admin/reports/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestCount").value(22))
                .andExpect(jsonPath("$.data.faultPileCount").value(2))
                .andExpect(jsonPath("$.data.totalFee").value(607.75));

        mvc.perform(get("/api/vehicles/V21/bills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].totalFee").value(16.50))
                .andExpect(jsonPath("$.data[0].priceBreakdown", containsString("09:50-10:00")))
                .andExpect(jsonPath("$.data[0].priceBreakdown", containsString("10:00-10:10")));
    }

    @Test
    void adminCanDisableEnablePileAndFilterReports() throws Exception {
        mvc.perform(post("/api/admin/clock/reset").param("time", "06:00"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/vehicles/VSTOP/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"F\",\"amountKwh\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CHARGING"))
                .andExpect(jsonPath("$.data.pileId").value("F1"));

        mvc.perform(post("/api/admin/piles/F1/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.piles[0].status").value("DISABLED"))
                .andExpect(jsonPath("$.data.piles[0].currentVehicle").value("VSTOP"));

        mvc.perform(post("/api/vehicles/VNEXT/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"F\",\"amountKwh\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pileId").value("F2"));

        mvc.perform(post("/api/admin/clock/advance").param("minutes", "2"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/vehicles/VSTOP/charging/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        mvc.perform(post("/api/admin/piles/F1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.piles[0].status").value("WORKING"));

        mvc.perform(get("/api/admin/reports/summary")
                        .param("scope", "DAY")
                        .param("vehicleId", "VSTOP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detailCount").value(1))
                .andExpect(jsonPath("$.data.totalDurationMinutes").value(2))
                .andExpect(jsonPath("$.data.piles[0].durationMinutes").value(2))
                .andExpect(jsonPath("$.data.piles[0].chargeFee").value(0.40))
                .andExpect(jsonPath("$.data.piles[0].serviceFee").value(0.80))
                .andExpect(jsonPath("$.data.piles[0].totalFee").value(1.20));

        mvc.perform(get("/api/admin/reports/details")
                        .param("scope", "DAY")
                        .param("pileId", "F1")
                        .param("vehicleId", "VSTOP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].detailNo").value("D2026051200001"));
    }
}
