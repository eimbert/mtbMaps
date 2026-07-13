package com.paygoon.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.paygoon.model.DestinationDescription;

public interface DestinationDescriptionRepository extends JpaRepository<DestinationDescription, Long> {
    List<DestinationDescription> findAllByPlaceKeyIn(Collection<String> keys);
}
