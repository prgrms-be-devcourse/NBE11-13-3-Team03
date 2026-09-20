package com.team3.gudit.outbox;


import com.team3.gudit.auth.oauth2.AuthProvider;
import com.team3.gudit.goods.domain.entity.Goods;
import com.team3.gudit.goods.domain.repository.GoodsRepository;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

//@Testcontainers //테스트 컨테이너를 JUnit이 아니라 Spring이 관리하도록 변경하기 위해 주석 처리
//@SpringBootTest가 전체 Spring 애플리케이션 컨텍스트를 실행했고, 메인 애플리케이션에 @EnableScheduling이 있어서 @Scheduled 메서드들이 자동으로 실행
@SpringBootTest
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
--tests 'com.team3.gudit.outbox.StockRestoreRecoveryE2ETest.connectToRedis'
* */