/**
 * 医馆 HIS —— 微信小程序患者端 全局配置
 *
 * 联调时只需要改这里：
 *  - 开发者工具（本机后端）：http://localhost:8080/api
 *  - 真机预览：http://<电脑局域网 IP>:8080/api（手机与电脑需在同一 WiFi）
 *  - 线上部署：https://<你的域名>/api（需在小程序后台配置 request 合法域名）
 */
module.exports = {
  // 后端接口基地址，结尾不要带斜杠
  BASE_URL: 'http://localhost:8080/api',

  // 开发环境固定短信验证码（仅用于界面提示，最终由后端校验）
  DEV_SMS_CODE: '123456',

  // 可预约日期范围：今天起 N 天
  BOOKING_DAYS: 7,

  // 请求超时时间（毫秒）
  REQUEST_TIMEOUT: 15000,

  // 就诊记录每页条数
  PAGE_SIZE: 10,

  // 应用信息（「我的 - 关于」展示）
  APP_NAME: '医馆 HIS',
  APP_VERSION: '1.0.0'
}
