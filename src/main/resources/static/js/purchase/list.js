const list =
    document.getElementById("purchase-list");

const filterButtons =
    document.querySelectorAll(".purchase-filter .filter-button");

let purchases = [];
let selectedStatus = "ALL";
let selectedPurchaseId = null;

document.addEventListener("DOMContentLoaded", () => {
    loadPurchases();
    bindFilterButtons();
    bindInquiryModal();
});

async function loadPurchases() {
    try {
        const response = await fetch(
            "/api/purchases",
            {
                credentials: "include"
            }
        );

        if (!response.ok) {
            if (response.status === 401) {
                alert("구매 내역을 확인하려면 로그인이 필요합니다.");
                window.location.href = "/oauth2/authorization/kakao";
                return;
            }

            throw new Error("구매 내역 조회에 실패했습니다.");
        }

        const result = await response.json();

        purchases = (result.purchases || [])
            .filter(purchase =>
                !(
                    purchase.status === "CANCELED"
                    && !purchase.purchasedAt
                )
            );

        renderPurchases();

    } catch (error) {
        console.error(error);

        list.innerHTML = `
            <p class="purchase-empty-message">
                구매 내역을 불러오지 못했습니다.
            </p>
        `;
    }
}

function bindFilterButtons() {
    filterButtons.forEach(button => {
        button.addEventListener("click", () => {
            selectedStatus =
                button.dataset.status;

            filterButtons.forEach(item => {
                item.classList.remove("active");
            });

            button.classList.add("active");

            renderPurchases();
        });
    });
}

function renderPurchases() {
    const filteredPurchases =
        selectedStatus === "ALL"
            ? purchases
            : purchases.filter(
                purchase =>
                    purchase.status === selectedStatus
            );

    if (filteredPurchases.length === 0) {
        list.innerHTML = `
            <p class="purchase-empty-message">
                해당 상태의 구매 내역이 없습니다.
            </p>
        `;

        return;
    }

    list.innerHTML =
        filteredPurchases
            .map(createPurchaseItem)
            .join("");

    bindInquiryButtons();
}

function bindInquiryButtons() {
    const inquiryButtons =
        document.querySelectorAll(".cs-inquiry-button");

    inquiryButtons.forEach(button => {
        button.addEventListener("click", () => {
            const purchaseId =
                button.dataset.purchaseId;

            openInquiryModal(purchaseId);
        });
    });
}

function bindInquiryModal() {
    const cancelButton =
        document.getElementById("cs-inquiry-cancel");

    const submitButton =
        document.getElementById("cs-inquiry-submit");

    const modal =
        document.getElementById("cs-inquiry-modal");

    cancelButton.addEventListener(
        "click",
        closeInquiryModal
    );

    submitButton.addEventListener(
        "click",
        submitInquiry
    );

    modal.addEventListener("click", event => {
        if (event.target === modal) {
            closeInquiryModal();
        }
    });
}

function openInquiryModal(purchaseId) {
    selectedPurchaseId = purchaseId;

    const modal =
        document.getElementById("cs-inquiry-modal");

    const textarea =
        document.getElementById("cs-inquiry-message");

    textarea.value = "";
    modal.classList.add("open");
    textarea.focus();
}

function closeInquiryModal() {
    selectedPurchaseId = null;

    const modal =
        document.getElementById("cs-inquiry-modal");

    modal.classList.remove("open");
}

async function submitInquiry() {
    const textarea =
        document.getElementById("cs-inquiry-message");

    const submitButton =
        document.getElementById("cs-inquiry-submit");

    const message =
        textarea.value.trim();

    if (!message) {
        alert("문의 내용을 입력해주세요.");
        return;
    }

    if (!selectedPurchaseId) {
        alert("구매 정보를 확인할 수 없습니다.");
        return;
    }

    try {
        submitButton.disabled = true;
        submitButton.textContent = "접수 중...";

        const response = await fetch(
            `/api/purchases/${selectedPurchaseId}/cs-inquiries`,
            {
                method: "POST",
                credentials: "include",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    message
                })
            }
        );

        if (response.status === 401) {
            alert("문의하려면 로그인이 필요합니다.");
            window.location.href = "/oauth2/authorization/kakao";
            return;
        }

        if (!response.ok) {
            throw new Error("문의 접수에 실패했습니다.");
        }

        alert("문의가 접수되었습니다.");
        closeInquiryModal();

    } catch (error) {
        console.error(error);
        alert("문의 접수 중 오류가 발생했습니다.");

    } finally {
        submitButton.disabled = false;
        submitButton.textContent = "문의 접수";
    }
}

function createPurchaseItem(purchase) {
    const statusInfo =
        getStatusInfo(purchase.status);

    return `
        <article class="purchase-item">

            <div class="purchase-thumbnail">
                ${createPurchaseImage(purchase)}
            </div>

            <div class="purchase-item-content">

                <div class="purchase-item-main">

                    <div>

                        <span class="purchase-status ${statusInfo.className}">
                            ${statusInfo.label}
                        </span>

                        <h2>
                            ${escapeHtml(purchase.goodsName)}
                        </h2>

                        <p class="purchase-item-price">
                            ${formatPrice(purchase.purchasePrice)}원
                        </p>

                    </div>

                    <div class="purchase-item-actions">

                        <a
                                href="/mypage/purchases/${purchase.purchaseId}"
                                class="detail-link-button">
                            상세 보기
                        </a>

                        <button
                                type="button"
                                class="cs-inquiry-button"
                                data-purchase-id="${purchase.purchaseId}">
                            문의하기
                        </button>

                    </div>

                </div>

                <div class="purchase-item-meta">

                    <div>
                        <span>수량</span>
                        <strong>
                            ${purchase.quantity}개
                        </strong>
                    </div>

                    <div>
                        <span>구매일</span>
                        <strong>
                            ${formatDateTime(purchase.purchasedAt)}
                        </strong>
                    </div>

                </div>

            </div>

        </article>
    `;
}

function createPurchaseImage(purchase) {
    if (purchase.imageUrl) {
        return `
            <img
                    src="${escapeHtml(purchase.imageUrl)}"
                    alt="${escapeHtml(purchase.goodsName)}"
                    class="purchase-image">
        `;
    }

    return `
        <div class="purchase-mock purchase-mock-keyring">
            GUDIT
        </div>
    `;
}

function getStatusInfo(status) {
    switch (status) {
        case "PENDING_PAYMENT":
            return {
                label: "결제 대기",
                className: "status-pending"
            };

        case "PURCHASED":
            return {
                label: "구매 완료",
                className: "status-purchased"
            };

        case "CANCELED":
            return {
                label: "취소",
                className: "status-canceled"
            };

        default:
            return {
                label: status,
                className: ""
            };
    }
}

function formatPrice(price) {
    return Number(price)
        .toLocaleString("ko-KR");
}

function formatDateTime(dateTime) {
    if (!dateTime) {
        return "-";
    }

    const date =
        new Date(dateTime);

    const year =
        date.getFullYear();

    const month =
        String(date.getMonth() + 1)
            .padStart(2, "0");

    const day =
        String(date.getDate())
            .padStart(2, "0");

    const hour =
        String(date.getHours())
            .padStart(2, "0");

    const minute =
        String(date.getMinutes())
            .padStart(2, "0");

    return `${year}.${month}.${day} ${hour}:${minute}`;
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}