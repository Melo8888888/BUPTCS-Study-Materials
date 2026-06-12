package com.evcharge.persistence;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PersistenceReportService {
    private final StationStateRepository stateRepository;
    private final ChargingPileRepository pileRepository;
    private final ChargingRequestRepository requestRepository;
    private final ChargingDetailRepository detailRepository;
    private final FaultEventRepository faultEventRepository;

    public PersistenceReportService(StationStateRepository stateRepository,
                                    ChargingPileRepository pileRepository,
                                    ChargingRequestRepository requestRepository,
                                    ChargingDetailRepository detailRepository,
                                    FaultEventRepository faultEventRepository) {
        this.stateRepository = stateRepository;
        this.pileRepository = pileRepository;
        this.requestRepository = requestRepository;
        this.detailRepository = detailRepository;
        this.faultEventRepository = faultEventRepository;
    }

    public Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("station_state", stateRepository.count());
        counts.put("charging_piles", pileRepository.count());
        counts.put("charging_requests", requestRepository.count());
        counts.put("charging_details", detailRepository.count());
        counts.put("fault_events", faultEventRepository.count());
        return counts;
    }
}
