package br.com.microservices.orchestrated.orderservice.core.service;

import br.com.microservices.orchestrated.orderservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.orderservice.core.document.Event;
import br.com.microservices.orchestrated.orderservice.core.dto.EventFilters;
import br.com.microservices.orchestrated.orderservice.core.repository.EventRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.util.ObjectUtils.*;

@Slf4j
@Service
@AllArgsConstructor
public class EventService {

    private final EventRepository eventRepository;


    public void notifyEnding(Event event) {
        event.setOrderId(event.getOrderId());
        event.setCreatedAt(LocalDateTime.now());
        save(event);
        log.info("Order {} with saga notified! TransactionId: {}", event.getOrderId(), event.getTransactionalId());
    }

    public List<Event> findAll() {
        return eventRepository.findAllByOrderByCreatedAtDesc();
    }
    public Event findByFilters(EventFilters eventFilters) {
        validateEventFilters(eventFilters);
        if (!isEmpty(eventFilters.getOrderId())) {
            return eventRepository
                    .findTop1ByOrderIdOrderByCreatedAtDesc(eventFilters.getOrderId())
                    .orElseThrow( () -> new ValidationException("Order id not found!"));
        } else {
            return eventRepository
                    .findTop1ByTransactionalIdOrderByCreatedAtDesc(
                            eventFilters.getTransactionalId())
                    .orElseThrow( () -> new ValidationException("Transaction id not found!"));
        }
    }

    private void validateEventFilters(EventFilters eventFilters) {
        if (isEmpty(eventFilters.getOrderId()) && isEmpty(eventFilters.getTransactionalId())){
            throw new ValidationException("Order or transactional id is required");
        }
    }

    public Event save(Event event){
        return eventRepository.save(event);
    }
}
