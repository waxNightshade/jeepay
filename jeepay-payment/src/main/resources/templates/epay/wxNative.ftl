<#ftl output_format="HTML" auto_esc=true>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>微信扫码支付</title>
    <style>
        body { margin: 0; font-family: sans-serif; color: #202124; background: #f5f7fa; }
        main { max-width: 420px; margin: 48px auto; padding: 28px; text-align: center; background: #fff; }
        img { width: 260px; height: 260px; max-width: 100%; }
        .amount { font-size: 28px; font-weight: 600; }
        .detail { color: #60646c; overflow-wrap: anywhere; }
    </style>
</head>
<body>
<main data-status-url="${statusUrl}">
    <h1>微信扫码支付</h1>
    <p class="amount">¥${money}</p>
    <img src="${codeImgUrl}" alt="微信支付二维码">
    <p class="detail">${name}</p>
    <p class="detail">订单号：${orderNo}</p>
    <p id="status-message" class="detail" aria-live="polite">等待支付</p>
</main>
<script>
    const main = document.querySelector('main');
    const statusMessage = document.getElementById('status-message');
    const statusUrl = main.dataset.statusUrl;

    async function poll() {
        try {
            const response = await fetch(statusUrl, {
                cache: 'no-store',
                credentials: 'same-origin'
            });
            if (!response.ok) {
                throw new Error('Unable to query payment status');
            }
            const result = await response.json();
            if (result.status === 'success' && result.returnUrl) {
                statusMessage.textContent = '支付成功';
                setTimeout(() => window.location.replace(result.returnUrl), 1000);
                return;
            }
            if (result.status === 'failed') {
                statusMessage.textContent = '支付失败，请重新发起支付';
                return;
            }
        } catch (error) {
            statusMessage.textContent = '正在确认支付结果';
        }
        setTimeout(poll, 2000);
    }

    poll();
</script>
</body>
</html>
