package com.team3.gudit.outbox;


import com.team3.gudit.auth.oauth2.AuthProvider;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.goods.domain.repository.GoodsRepository;
import com.team3.gudit.outbox.consumer.StockRestoreConsumer;
import com.team3.gudit.payment.entity.Payment;
import com.team3.gudit.payment.entity.PaymentStatus;
import com.team3.gudit.payment.repository.PaymentRepository;
import com.team3.gudit.purchase.dto.PurchaseCreateResponse;
import com.team3.gudit.purchase.entity.Purchase;
import com.team3.gudit.purchase.entity.PurchaseStatus;
import com.team3.gudit.purchase.repository.PurchaseRepository;
import com.team3.gudit.purchase.service.PurchaseService;
import com.team3.gudit.sale.domain.entity.Sale;
import com.team3.gudit.sale.domain.enums.SaleStatus;
import com.team3.gudit.sale.domain.repository.SaleRepository;
import com.team3.gudit.sale.service.SaleService;
import com.team3.gudit.user.domain.entity.Role;
import com.team3.gudit.user.domain.entity.User;
import com.team3.gudit.user.domain.repository.UserRepository;
import com.team3.gudit.outbox.entity.OutboxEvent;
import com.team3.gudit.outbox.entity.OutboxEventStatus;
import com.team3.gudit.outbox.entity.OutboxEventType;
import com.team3.gudit.outbox.repository.OutboxEventRepository;
import com.team3.gudit.outbox.publisher.OutboxPublisher;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.data.redis.connection.stream.StreamRecords;

import java.util.Map;
import java.time.LocalDateTime;
import java.util.List;


import static com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_GROUP;
import static com.team3.gudit.outbox.publisher.StockRestoreStreamConstants.STOCK_RESTORE_STREAM;
import static org.assertj.core.api.Assertions.assertThat;

//@Testcontainers
// 테스트 컨테이너를 JUnit이 아니라 Spring이 관리하도록 변경하기 위해 주석 처리
//@SpringBootTest가 전체 Spring 애플리케이션 컨텍스트를 실행했고, 메인 애플리케이션에 @EnableScheduling이 있어서 @Scheduled 메서드들이 자동으로 실행
//테스트에서는 Publisher와 Consumer가 자동 실행되지 않고, 이후 각 메서드를 직접 호출 가능
@SpringBootTest(
        properties = "gudit.scheduling.enabled=false"
)
@Import(StockRestoreRecoveryE2ETest.ContainerConfiguration.class)
class StockRestoreRecoveryE2ETest {


    @Autowired
    private PurchaseService purchaseService;

    @Autowired
    private SaleService saleService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private PurchaseRepository purchaseRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisher outboxPublisher;

    @Autowired
    private StockRestoreConsumer stockRestoreConsumer;

    @Test
    @DisplayName("복구 E2E 테스트용 Redis 컨테이너에 연결된다.")
    void connectToRedis() {
        String response = redisTemplate
                .getConnectionFactory()
                .getConnection()
                .ping();

        assertThat(response).isEqualTo("PONG");
    }

    @Test
    @DisplayName("정상 구매 시 Redis 재고를 차감하고 결제 대기 상태를 저장한다.")
    void purchaseNormally(){
        User user = userRepository.save(
                User.builder()
                        .kakaoId(9001L)
                        .nickname("outbox-e2e-user")
                        .email("outbox-e2e@test.com")
                        .role(Role.USER)
                        .provider(AuthProvider.KAKAO)
                        .build()
        );

        Goods goods = goodsRepository.save(
                Goods.of(
                        "Outbox E2E 상품",
                        "재고 복구 테스트 상품",
                        15_000,
                        "test-image.jpg"
                )
        );

        LocalDateTime now = LocalDateTime.now();

        Sale sale = saleRepository.save(
                Sale.builder()
                        .goods(goods)
                        .createdBy(user.getId())
                        .initialStock(10)
                        .remainingStock(10)
                        .maxPurchaseQuantity(1)
                        .status(SaleStatus.READY)
                        .startAt(now.minusMinutes(1))
                        .endAt(now.plusMinutes(10))
                        .build()
        );

        saleService.warmupSaleInfo(sale.getId());

        saleService.startSale(sale.getId());

        String stockKey =
                "sale:" + sale.getId() + ":stock";

        String userPurchaseKey =
                "sale:" + sale.getId()
                        + ":user:" + user.getId();

        // Redis 초기 상태 확인
        assertThat(
                redisTemplate.opsForValue().get(stockKey)
        ).isEqualTo("10");

        assertThat(
                redisTemplate.opsForValue().get(userPurchaseKey)
        ).isNull();

        // when: 정상 구매
        PurchaseCreateResponse response =
                purchaseService.purchase(
                        user.getId(),
                        sale.getId()
                );

        // then: 실제 DB에서 구매와 결제 조회
        Purchase purchase = purchaseRepository
                .findById(response.purchaseId())
                .orElseThrow();

        Payment payment = paymentRepository
                .findByPurchaseId(purchase.getId())
                .orElseThrow();

        // then: Redis 재고 및 사용자 구매 수량 확인
        assertThat(
                redisTemplate.opsForValue().get(stockKey)
        ).isEqualTo("9");

        assertThat(
                redisTemplate.opsForValue().get(userPurchaseKey)
        ).isEqualTo("1");

        // then: DB 구매 상태 확인
        assertThat(purchase.getStatus())
                .isEqualTo(PurchaseStatus.PENDING_PAYMENT);

        assertThat(purchase.getQuantity())
                .isEqualTo(1);

        assertThat(purchase.getPurchasePrice())
                .isEqualTo(15_000);

        // then: DB 결제 상태 확인
        assertThat(payment.getStatus())
                .isEqualTo(PaymentStatus.READY);

        assertThat(payment.getOrderId())
                .isEqualTo(response.orderId());
    }

    @Test
    @DisplayName("구매 취소 후 Outbox와 Redis Stream을 거쳐 재고를 복구한다")
    void recoverStockEndToEndWhenPurchaseIsCanceled() {
        // given: 사용자 생성
        User user = userRepository.save(
                User.builder()
                        .kakaoId(9002L)
                        .nickname("outbox-cancel-user")
                        .email("outbox-cancel@test.com")
                        .role(Role.USER)
                        .provider(AuthProvider.KAKAO)
                        .build()
        );

        // given: 상품 생성
        Goods goods = goodsRepository.save(
                Goods.of(
                        "Outbox 취소 테스트 상품",
                        "재고 복구 이벤트 확인용",
                        15_000,
                        "test-image.jpg"
                )
        );

        LocalDateTime now = LocalDateTime.now();

        // given: 판매 생성
        Sale sale = saleRepository.save(
                Sale.builder()
                        .goods(goods)
                        .createdBy(user.getId())
                        .initialStock(10)
                        .remainingStock(10)
                        .maxPurchaseQuantity(1)
                        .status(SaleStatus.READY)
                        .startAt(now.minusMinutes(1))
                        .endAt(now.plusMinutes(10))
                        .build()
        );

        // given: Redis 판매 정보 준비 및 판매 시작
        saleService.warmupSaleInfo(sale.getId());
        saleService.startSale(sale.getId());

        // given: 정상 구매
        PurchaseCreateResponse purchaseResponse =
                purchaseService.purchase(
                        user.getId(),
                        sale.getId()
                );

        String stockKey =
                "sale:" + sale.getId() + ":stock";

        String userPurchaseKey =
                "sale:" + sale.getId()
                        + ":user:" + user.getId();

        // 구매 직후 Redis 상태
        assertThat(
                redisTemplate.opsForValue().get(stockKey)
        ).isEqualTo("9");

        assertThat(
                redisTemplate.opsForValue().get(userPurchaseKey)
        ).isEqualTo("1");

        // when: 결제 대기 구매 취소
        purchaseService.cancel(
                user.getId(),
                purchaseResponse.purchaseId()
        );

        // then: 구매 상태 확인
        Purchase canceledPurchase = purchaseRepository
                .findById(purchaseResponse.purchaseId())
                .orElseThrow();

        assertThat(canceledPurchase.getStatus())
                .isEqualTo(PurchaseStatus.CANCELED);

        // then: 결제 상태 확인
        Payment canceledPayment = paymentRepository
                .findByPurchaseId(canceledPurchase.getId())
                .orElseThrow();

        assertThat(canceledPayment.getStatus())
                .isEqualTo(PaymentStatus.CANCELED);

        // then: PENDING Outbox 이벤트 확인
        List<OutboxEvent> pendingEvents =
                outboxEventRepository
                        .findAllByStatusOrderByCreatedAtAsc(
                                OutboxEventStatus.PENDING
                        );

        assertThat(pendingEvents).hasSize(1);

        OutboxEvent outboxEvent = pendingEvents.getFirst();

        assertThat(outboxEvent.getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);

        assertThat(outboxEvent.getEventType())
                .isEqualTo(
                        OutboxEventType.STOCK_RESTORE_REQUESTED
                );

        // then: 아직 Consumer가 처리하지 않았으므로
        // Redis 재고는 복구되지 않은 상태
        // 현재까지는 Outbox만 저장됐고
        // Consumer가 처리하지 않았으므로 Redis 재고는 여전히 9
        assertThat(
                redisTemplate.opsForValue().get(stockKey)
        ).isEqualTo("9");

        assertThat(
                redisTemplate.opsForValue().get(userPurchaseKey)
        ).isEqualTo("1");

        // when: Outbox Publisher 직접 실행
        outboxPublisher.publishPendingEvents();

        // then: Outbox 상태가 PUBLISHED로 변경됨
        OutboxEvent publishedEvent = outboxEventRepository
                .findById(outboxEvent.getId())
                .orElseThrow();

        assertThat(publishedEvent.getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);

        // then: Redis Stream에 현재 eventId가 포함된 메시지가 발행됨
        List<MapRecord<String, Object, Object>> streamRecords =
                redisTemplate.opsForStream().range(
                        STOCK_RESTORE_STREAM,
                        Range.unbounded()
                );

        assertThat(streamRecords)
                .isNotNull()
                .anySatisfy(record -> {
                    assertThat(
                            record.getValue().get("eventId")
                    ).isEqualTo(outboxEvent.getEventId());

                    assertThat(
                            record.getValue().get("eventType")
                    ).isEqualTo(
                            OutboxEventType
                                    .STOCK_RESTORE_REQUESTED
                                    .name()
                    );

                    assertThat(
                            record.getValue().get("payload")
                    ).isEqualTo(outboxEvent.getPayload());
                });

        // then: Publisher는 메시지만 발행하므로
        // 아직 Redis 재고를 복구하지 않음
        assertThat(
                redisTemplate.opsForValue().get(stockKey)
        ).isEqualTo("9");

        assertThat(
                redisTemplate.opsForValue().get(userPurchaseKey)
        ).isEqualTo("1");

        // when: Consumer 실행
        stockRestoreConsumer.consume();

        // then: Redis 재고 복구 확인
        assertThat(
                redisTemplate.opsForValue().get(stockKey)
        ).isEqualTo("10");

        assertThat(
                redisTemplate.opsForValue().get(userPurchaseKey)
        ).isNull();

        // then: 처리된 eventId가 Redis에 기록됨
        String processedEventKey =
                "stock-restore:processed:"
                        + outboxEvent.getEventId();

        assertThat(
                redisTemplate.opsForValue().get(processedEventKey)
        ).isEqualTo("1");

        // then: Consumer가 정상 처리한 메시지를 ACK하여 Pending이 남지 않음
        var pendingSummary =
                redisTemplate.opsForStream().pending(
                        STOCK_RESTORE_STREAM,
                        STOCK_RESTORE_GROUP
                );

        assertThat(pendingSummary).isNotNull();

        assertThat(
                pendingSummary.getTotalPendingMessages()
        ).isZero();

        // then: Consumer 처리 후에도 Outbox는 PUBLISHED 상태 유지
        OutboxEvent completedEvent = outboxEventRepository
                .findById(outboxEvent.getId())
                .orElseThrow();

        assertThat(completedEvent.getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHED);

        // when: 같은 eventId를 가진 메시지가 다시 전달되는 상황 재현
        redisTemplate.opsForStream().add(
                StreamRecords
                        .newRecord()
                        .in(STOCK_RESTORE_STREAM)
                        .ofMap(
                                Map.of(
                                        "eventId",
                                        outboxEvent.getEventId(),
                                        "traceId",
                                        outboxEvent.getTraceId() == null
                                                ? ""
                                                : outboxEvent.getTraceId(),
                                        "eventType",
                                        outboxEvent
                                                .getEventType()
                                                .name(),
                                        "payload",
                                        outboxEvent.getPayload()
                                )
                        )
        );

        // when: Consumer가 중복 메시지를 다시 처리
        stockRestoreConsumer.consume();

        // then: 이미 처리된 eventId이므로 재고가 중복 복구되지 않음
        assertThat(
                redisTemplate.opsForValue().get(stockKey)
        ).isEqualTo("10");

        assertThat(
                redisTemplate.opsForValue().get(userPurchaseKey)
        ).isNull();

        // then: 중복 메시지도 정상 ACK되어 Pending이 남지 않음
        var pendingAfterDuplicate =
                redisTemplate.opsForStream().pending(
                        STOCK_RESTORE_STREAM,
                        STOCK_RESTORE_GROUP
                );

        assertThat(pendingAfterDuplicate).isNotNull();

        assertThat(
                pendingAfterDuplicate.getTotalPendingMessages()
        ).isZero();
    }



    @TestConfiguration(proxyBeanMethods = false)
    static class ContainerConfiguration {

        @Bean
        @ServiceConnection(name = "redis")
        GenericContainer<?> redisContainer() {
            return new GenericContainer<>(
                    DockerImageName.parse("redis:7-alpine")
            ).withExposedPorts(6379);
        }
    }
}

/*
bash gradlew test \
--tests 'com.team3.gudit.outbox.StockRestoreRecoveryE2ETest'
* */