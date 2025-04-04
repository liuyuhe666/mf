package cn.com.mfish.oauth.controller;

import cn.com.mfish.common.core.exception.MyRuntimeException;
import cn.com.mfish.common.core.exception.OAuthValidateException;
import cn.com.mfish.common.core.utils.Utils;
import cn.com.mfish.common.core.web.Result;
import cn.com.mfish.common.oauth.common.SerConstant;
import cn.com.mfish.common.oauth.entity.WeChatToken;
import cn.com.mfish.common.oauth.validator.WeChatTokenValidator;
import cn.com.mfish.common.core.utils.QRCodeUtils;
import cn.com.mfish.oauth.entity.QRCode;
import cn.com.mfish.oauth.entity.QRCodeImg;
import cn.com.mfish.oauth.entity.RedisQrCode;
import cn.com.mfish.oauth.service.QRCodeService;
import com.google.zxing.WriterException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Base64;
import java.util.UUID;

/**
 * @author: mfish
 * @date: 2020/3/5 14:54
 */
@RestController
@Slf4j
@RequestMapping("/qrCodeLogin")
@Tag(name = "扫码登录接口")
public class QRCodeController {
    @Resource
    QRCodeService qrCodeService;
    @Resource
    WeChatTokenValidator weChatTokenValidator;

    @Operation(summary = "生成二维码")
    @GetMapping("/build")
    public Result<QRCodeImg> buildQRCode() {
        String error = "错误:生成二维码异常!";
        try {
//            Hashtable<EncodeHintType, Comparable> hints = new Hashtable<>();
//            //指定二维码字符集编码
//            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
//            //设置二维码纠错等级
//            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
//            //设置图片边距
//            hints.put(EncodeHintType.MARGIN, 2);
//            String code = UUID.randomUUID().toString();
//            BitMatrix matrix = new MultiFormatWriter().encode(code,
//                    BarcodeFormat.QR_CODE, 250, 250, hints);
//            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
//            BufferedImage bufferedImage = MatrixToImageWriter.toBufferedImage(matrix);
            String code = Utils.uuid32();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            BufferedImage bufferedImage = QRCodeUtils.createQRCode(code, "/static/img/logo.png");
            if (ImageIO.write(bufferedImage, "png", byteArrayOutputStream)) {
                RedisQrCode qrCode = saveQRCode(code);
                return Result.ok(buildResponseCode(qrCode, byteArrayOutputStream));
            }
            throw new MyRuntimeException(error);
        } catch (WriterException | IOException e) {
            log.error(error, e);
            throw new MyRuntimeException(error);
        }
    }

    /**
     * 保存二维码
     *
     * @param code code
     * @return 二维码对象
     */
    private RedisQrCode saveQRCode(String code) {
        RedisQrCode qrCode = new RedisQrCode();
        qrCode.setCode(code);
        qrCode.setStatus(SerConstant.ScanStatus.未扫描.toString());
        qrCodeService.saveQRCode(qrCode);
        return qrCode;
    }

    /**
     * 构建返回的二维码图像
     * <p>
     * 本方法用于生成二维码图像的响应数据，它将二维码内容编码为Base64字符串，
     * 并封装其他相关信息如状态和代码本身
     *
     * @param qrCode           二维码对象，包含二维码的内容和状态
     * @param byteArrayOutputStream 用于输出二维码图像的字节流，此处将被转换为Base64字符串
     * @return QRCodeImg       返回一个二维码图像对象，包含图像的Base64字符串、二维码代码和状态
     */
    private QRCodeImg buildResponseCode(QRCode qrCode, ByteArrayOutputStream byteArrayOutputStream) {
        QRCodeImg qrCodeImg = new QRCodeImg();
        qrCodeImg.setImg("data:image/png;base64," + Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray()));
        qrCodeImg.setCode(qrCode.getCode());
        qrCodeImg.setStatus(qrCode.getStatus());
        return qrCodeImg;
    }

    @Operation(summary = "检测扫码登录状态")
    @GetMapping("/check")
    @Parameters({
            @Parameter(name = SerConstant.QR_CODE, description = "二维码生成的code值", required = true)
    })
    public Result<QRCode> qrCodeLoginCheck(String code) throws InvocationTargetException, IllegalAccessException {
        RedisQrCode redisQrCode = qrCodeService.checkQRCode(code);
        if (redisQrCode == null) {
            return Result.ok(null, "未检测到扫码状态");
        }
        QRCode qrCode = new QRCode();
        BeanUtils.copyProperties(qrCode, redisQrCode);
        return Result.ok(qrCode);
    }

    @Operation(summary = "扫描二维码登录")
    @PostMapping("/scan")
    @Parameters({
            @Parameter(name = SerConstant.QR_CODE, description = "二维码生成的code值", required = true)
    })
    public Result<String> scanQrCode(HttpServletRequest request) {
        return qrCodeOperator(request, SerConstant.ScanStatus.未扫描, SerConstant.ScanStatus.已扫描);
    }

    @Operation(summary = "扫码确认登录")
    @PostMapping("/login")
    @Parameters({
            @Parameter(name = SerConstant.QR_CODE, description = "二维码生成的code值", required = true),
            @Parameter(name = SerConstant.QR_SECRET, description = "前一次扫码返回的密钥", required = true)
    })
    public Result<String> qrCodeLogin(HttpServletRequest request) {
        return qrCodeOperator(request, SerConstant.ScanStatus.已扫描, SerConstant.ScanStatus.已确认);
    }

    @Operation(summary = "扫码取消登录")
    @PostMapping("/cancel")
    @Parameters({
            @Parameter(name = SerConstant.QR_CODE, description = "二维码生成的code值", required = true),
            @Parameter(name = SerConstant.QR_SECRET, description = "前一次扫码返回的密钥", required = true)
    })
    public Result<String> qrCodeCancel(HttpServletRequest request) {
        return qrCodeOperator(request, SerConstant.ScanStatus.已扫描, SerConstant.ScanStatus.已取消);
    }

    /**
     * 扫码登录操作
     *
     * @param request 入参，用于获取扫码相关信息
     * @param origStatus 当前二维码状态
     * @param destStatus 目标二维码状态
     * @return 返回操作结果，包括一个随机生成的密钥
     */
    private Result<String> qrCodeOperator(HttpServletRequest request, SerConstant.ScanStatus origStatus, SerConstant.ScanStatus destStatus) {
        Result<WeChatToken> result = weChatTokenValidator.validate(request);
        if (!result.isSuccess()) {
            throw new OAuthValidateException(result.getMsg());
        }
        String code = request.getParameter(SerConstant.QR_CODE);
        RedisQrCode redisQrCode = qrCodeService.checkQRCode(code);
        if (redisQrCode == null) {
            return Result.fail("错误:code不正确");
        }
        if (!StringUtils.isEmpty(redisQrCode.getAccessToken())
                && !result.getData().getAccess_token().equals(redisQrCode.getAccessToken())) {
            return Result.fail("错误:两次请求token不相同");
        }
        if (!StringUtils.isEmpty(redisQrCode.getSecret())) {
            String secret = request.getParameter(SerConstant.QR_SECRET);
            if (!redisQrCode.getSecret().equals(secret)) {
                return Result.fail("错误:传入密钥不正确");
            }
        }
        if (origStatus.toString().equals(redisQrCode.getStatus())) {
            redisQrCode.setStatus(destStatus.toString());
            redisQrCode.setAccessToken(result.getData().getAccess_token());
            redisQrCode.setAccount(result.getData().getAccount());
            redisQrCode.setSecret(UUID.randomUUID().toString());
            qrCodeService.updateQRCode(redisQrCode);
            return Result.ok(redisQrCode.getSecret(), "操作成功");
        }
        return Result.fail("错误:二维码状态不正确");
    }
}
