package br.com.microservices.orchestrated.paymentservice.core.service;


import br.com.microservices.orchestrated.paymentservice.config.exception.ValidationException;
import br.com.microservices.orchestrated.paymentservice.core.dto.Event;
import br.com.microservices.orchestrated.paymentservice.core.dto.History;
import br.com.microservices.orchestrated.paymentservice.core.dto.OrderProducts;
import br.com.microservices.orchestrated.paymentservice.core.enums.EPaymentStatus;
import br.com.microservices.orchestrated.paymentservice.core.enums.ESagaStatus;
import br.com.microservices.orchestrated.paymentservice.core.model.Payment;
import br.com.microservices.orchestrated.paymentservice.core.producer.KafkaProducer;
import br.com.microservices.orchestrated.paymentservice.core.repository.PaymentRepository;
import br.com.microservices.orchestrated.paymentservice.core.utils.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@AllArgsConstructor
public class PaymentService {

    private static final String CURRENT_SOURCE = "PAYMENT_SERVICE";

    private final JsonUtil jsonUtil;
    private final KafkaProducer producer;
    private final PaymentRepository paymentRepository;
    private final Double REDUCE_SUM_VALUE = 0.0;

    public void realizePayment(Event event) {
        try {
            checkCurrentValidation(event);
            createPedingPayment(event);
            var payment = findByOrderIdAndTransactionId(event);
            changePaymentToSuccess(payment);
            handleSuccess(event);
        } catch (Exception e) {
            log.error("Error trying to make payment: ", e);
            handleFailCurrentNotExecuted(event, e.getMessage());
        }
        producer.sendEvent(jsonUtil.toJson(event));
    }


    private void checkCurrentValidation(Event event) {
        if (paymentRepository.existsByOrderIdAndTransactionId(
                event.getOrderId(), event.getTransactionId())) {
            throw new ValidationException("There's another transactionId for this validation.");
        }
    }

    private void createPedingPayment(Event event) {
        var totalAmount = calculateAmount(event);
        var totalItems = calculateTotalItems(event);

        var payment = Payment.builder()
                .orderId(event.getPayload().getId())
                .transactionId(event.getTransactionId())
                .totalAmount(totalAmount)
                .totalItems(totalItems)
                .build();
        save(payment);
        setEventAmountItems(event, payment);
    }

    private double calculateAmount(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(product -> product.getQuantity() * product.getProduct().getValue())
                .reduce(REDUCE_SUM_VALUE, Double::sum);
    }

    private int calculateTotalItems(Event event) {
        return event
                .getPayload()
                .getProducts()
                .stream()
                .map(OrderProducts::getQuantity)
                .reduce(REDUCE_SUM_VALUE.intValue(), Integer::sum);
    }

    private void setEventAmountItems(Event event, Payment payment) {
        event.getPayload().setTotalAmount(payment.getTotalAmount());
        event.getPayload().setTotalItems(payment.getTotalItems());
    }

    private Payment findByOrderIdAndTransactionId(Event event) {
        return paymentRepository
                .findByOrderIdAndTransactionId(event.getPayload().getId(), event.getTransactionId())
                .orElseThrow(() -> new ValidationException("Payment not found by OrderId and TransactionId"));
    }

    private void validateAmount(double amount) {
        double MIN_AMOUNT_VALUE = 0.1;
        if (amount < MIN_AMOUNT_VALUE) {
            throw new ValidationException("Amount must be greater than " + MIN_AMOUNT_VALUE);
        }
    }

    private void changePaymentToSuccess(Payment payment) {
        payment.setStatus(EPaymentStatus.SUCCESS);
        save(payment);
    }

    private void handleSuccess(Event event) {
        event.setStatus(ESagaStatus.SUCCESS);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Payment realized successfully!");
    }

    private void addHistory(Event event, String message) {
        var history = History
                .builder()
                .source(event.getSource())
                .status(event.getStatus())
                .message(message)
                .createdAt(LocalDateTime.now())
                .build();
        event.addToHistory(history);
    }

    private void handleFailCurrentNotExecuted(Event event, String message) {
        event.setStatus(ESagaStatus.ROLLBACK_PENDING);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Fail to realize payment: ".concat(message));
    }

    public void realizeRefunding(Event event) {
        changePaymentToRefund(event);
        event.setStatus(ESagaStatus.FAIL);
        event.setSource(CURRENT_SOURCE);
        addHistory(event, "Rollback executed for payment!");
        producer.sendEvent(jsonUtil.toJson(event));
    }

    private void changePaymentToRefund(Event event) {
        var payment = findByOrderIdAndTransactionId(event);
        payment.setStatus(EPaymentStatus.REFUND);
        setEventAmountItems(event, payment);
        save(payment);
    }


    private void save(Payment payment) {
        paymentRepository.save(payment);
    }
}
