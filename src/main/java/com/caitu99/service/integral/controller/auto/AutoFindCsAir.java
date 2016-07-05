package com.caitu99.service.integral.controller.auto;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.caitu99.service.constants.SysConstants;
import com.caitu99.service.integral.controller.auto.abs.AutoFindImageAbstract;
import com.caitu99.service.integral.controller.spider.ManualQueryCsAir;
import com.caitu99.service.integral.controller.spider.abs.ManualQueryAbstract;
import com.caitu99.service.integral.domain.AutoFindRecord;
import com.caitu99.service.integral.domain.CardType;
import com.caitu99.service.integral.domain.UserCardManual;
import com.caitu99.service.integral.service.CardTypeService;
import com.caitu99.service.integral.service.ManualLoginService;
import com.caitu99.service.integral.service.UserCardManualService;
import com.caitu99.service.utils.ApiResultCode;
import com.caitu99.service.utils.json.JsonResult;

/**
 * 南航自动发现
 * @Description: (类职责详细描述,可空) 
 * @ClassName: AutoFindAirChina 
 * @author xiongbin
 * @date 2015年12月16日 下午6:00:03 
 * @Copyright (c) 2015-2020 by caitu99
 */
@Component
public class AutoFindCsAir extends AutoFindImageAbstract{

	private final static Logger logger = LoggerFactory.getILoggerFactory().getLogger("autoAndRefreshFileLogger");
	
	@Autowired
	private ManualQueryCsAir manualQueryCsAir;
	@Autowired
	private ManualLoginService manualLoginService;
	@Autowired
	private UserCardManualService userCardManualService;
	@Autowired
	private CardTypeService cardTypeService;

	private String MAIL_ERR_MSG = "【手动查询自动更新异常】：userId:{},manalId:{},account:{},errMsg:{}";
	private String MAIL_WARN_MSG = "【手动查询自动更新警告】：userId:{},manalId:{},account:{},warnMsg:{}";
	private String MAIL_INFO_MSG = "【手动查询自动更新信息】：userId:{},manalId:{},account:{},infoMsg:{}";
	
	public String login(Long userId,String loginAccount,String password,Integer count){
		return super.login(manualQueryCsAir, userId, loginAccount, password, count, "南航");
	}
	
	/**
	 * 自动更新登录
	 * 	
	 * @Description: (方法职责详细描述,可空)  
	 * @Title: loginForUpdate 
	 * @param userId
	 * @param loginAccount
	 * @param password
	 * @return
	 * @date 2015年12月18日 下午2:56:47  
	 * @author ws
	 */
	public String loginForUpdate(Long userId,String loginAccount,String password){

		if(null == userId || StringUtils.isBlank(loginAccount) || StringUtils.isBlank(password)){
			return null;
		}
		
		String resultJson = super.loginForUpdate(manualQueryCsAir, userId, loginAccount, password);
		
		JSONObject json = JSON.parseObject(resultJson);
		//resultJson.put("code", ApiResultCode.IMAGECODE_ERROR);
		if(ApiResultCode.IMAGECODE_ERROR == json.getInteger("code")){//验证码错误，再试一次
			resultJson = super.loginForUpdate(manualQueryCsAir, userId, loginAccount, password);
		}
		return resultJson;
	}

	@Override
	public String checkResult(String jsonString,ManualQueryAbstract manualQuery, 
										Long userId, String loginAccount,String password, Integer count, String log) {
		
		String reslut = super.checkResult(jsonString, manualQuery, userId, loginAccount, password, count, log);
		
		if(StringUtils.isBlank(reslut)){
			JSONObject json = JSON.parseObject(jsonString);
			Integer code = json.getInteger("code");
			
			JSONObject reslutJSON = new JSONObject();
			//您输入的用户名、密码不匹配哟，请您检查后再试试
			if(code.equals(2005)){
				logger.info("【手动查询自动发现失败】:" + "userId：" + userId + "," + log + "自动发现,尝试登陆失败." + "用户名、密码不匹配,账号存在");
				
				reslutJSON.put("code", AutoFindRecord.STATUS_LOGINACCUNT_EXIST);
				reslutJSON.put("messsage", "用户名、密码不匹配");
				reslutJSON.put("error", 2005);
				
				return reslutJSON.toJSONString();
			}
			
			logger.warn("【手动查询自动发现失败】:" + "错误信息未捕获到");
			return null;
		}
			
		return reslut;
	}

	/* (non-Javadoc)
	 * @see com.caitu99.service.integral.controller.auto.abs.AutoFindAbstract#saveResult(java.lang.Long, java.lang.Long, java.lang.String, java.lang.String)
	 */
	@Override
	public String saveResult(Long userId, Long manualId,String loginAccount, String password,
			String result, String version) {
		if(StringUtils.isBlank(result)){
			return "账号信息不全";
		}
		//return ApiResult.outSucceed(ApiResultCode.LOGINACCOUNT_PASSWORD_ERROR, "账号或密码错误 ");

		JSONObject json = JSON.parseObject(result);
		
		boolean flag = JsonResult.checkResult(result,ApiResultCode.SUCCEED);
		if(!flag){
			Integer code = json.getInteger("code");
			if(ApiResultCode.LOGINACCOUNT_PASSWORD_ERROR.equals(code)){//账号或密码错误
				try {
					pwdErrHandler(userId, manualId, loginAccount, password);
					logger.warn(MAIL_WARN_MSG,userId,manualId,loginAccount,"账号或密码错误");
				} catch (Exception e) {
					logger.error(MAIL_ERR_MSG,userId,manualId,loginAccount,e);
				}
				
				return "账号或密码错误";
			}else{
				//未更新成功
				logger.warn(MAIL_WARN_MSG,userId,manualId,loginAccount,"该账户自动更新失败");
				return json.getString("message");
			}
		}

		Map<String,Object> addInfo = saveData(userId, loginAccount, json, version);
		addInfo.put("message", SysConstants.SUCCESS);
		
		logger.info(MAIL_INFO_MSG,userId,manualId,loginAccount,"该账户自动更新成功");
		return JSON.toJSONString(addInfo);
	}

	public Map<String,Object> saveData(Long userId, String loginAccount, JSONObject json, String version) {
		String jsonString = json.getString("data");
		JSONObject jsonObject = JSON.parseObject(jsonString);
		
//		{"code":0,"data":"{\"integral\":\"1,255公里\",\"name\":\"曹君跃\",\"card\":\"卡号 230002631681\"}","message":"0"}
		
		Integer integral = jsonObject.getInteger("integral");
		String userName = jsonObject.getString("name");
		String cardNo = jsonObject.getString("card");
		
		Date now = new Date();

		Map<String,Object> resData = new HashMap<String, Object>();
		resData.put("userId", String.valueOf(userId));
		
		/**
		 * 记录用户积分数据 
		 */
		UserCardManual csAirManual = userCardManualService.getUserCardManualSelective(userId,UserCardManual.CSAIR_INTEGRAL,cardNo,null,null);
		
		if(null == csAirManual){
			//计算并赠送财币
			presentTubiService.presentTubiDo(userId, UserCardManual.CSAIR_INTEGRAL, 0, integral, resData, version);
			
			csAirManual = new UserCardManual();
			csAirManual.setIntegral(integral);
			csAirManual.setUserName(userName);
			csAirManual.setCardNo(cardNo);
			csAirManual.setGmtModify(now);
			csAirManual.setGmtCreate(now);
			csAirManual.setUserId(userId);
			csAirManual.setLoginAccount(loginAccount);
			csAirManual.setCardTypeId(UserCardManual.CSAIR_INTEGRAL);
			csAirManual.setType(UserCardManual.TYPE_AUTO_FIND_IMPORT);
		}else{
			//积分变更消息推送
			if(null != csAirManual.getCardTypeId() 
					&& null != csAirManual.getIntegral()
					&& null != integral){
				pushIntegralChangeMessage(userId, csAirManual.getCardTypeId(), integral, integral-csAirManual.getIntegral());
			}

			//计算并赠送财币
			presentTubiService.presentTubiDo(userId, UserCardManual.CSAIR_INTEGRAL, csAirManual.getIntegral(), integral, resData, version);
			
			csAirManual.setIntegral(integral);
			csAirManual.setUserName(userName);
			csAirManual.setCardNo(cardNo);
			csAirManual.setLoginAccount(loginAccount);
			csAirManual.setGmtModify(now);
		}
		
		userCardManualService.insertORupdate(csAirManual);
		
		return resData;
	}
	
	public Integer saveDataAutoFind(Long userId, String loginAccount, JSONObject json) {
		String jsonString = json.getString("data");
		JSONObject jsonObject = JSON.parseObject(jsonString);
		
//		{"code":0,"data":"{\"integral\":\"1,255公里\",\"name\":\"曹君跃\",\"card\":\"卡号 230002631681\"}","message":"0"}
		
		Integer integral = jsonObject.getInteger("integral");
		String userName = jsonObject.getString("name");
		String cardNo = jsonObject.getString("card");
		
		Date now = new Date();
		
		/**
		 * 记录用户积分数据 
		 */
		UserCardManual csAirManual = userCardManualService.getUserCardManualSelective(userId,UserCardManual.CSAIR_INTEGRAL,cardNo,null,null);
		
		if(null == csAirManual){
			csAirManual = new UserCardManual();
			csAirManual.setIntegral(integral);
			csAirManual.setUserName(userName);
			csAirManual.setCardNo(cardNo);
			csAirManual.setGmtModify(now);
			csAirManual.setGmtCreate(now);
			csAirManual.setUserId(userId);
			csAirManual.setLoginAccount(loginAccount);
			csAirManual.setCardTypeId(UserCardManual.CSAIR_INTEGRAL);
			csAirManual.setType(UserCardManual.TYPE_AUTO_FIND_IMPORT);
			userCardManualService.insert(csAirManual);
		}else{
			logger.info("【手动查询自动发现】:" + "userId:" + userId + ",loginAccount:" + loginAccount + ",南航积分数据已存在.");
		}
		
		CardType cardType = cardTypeService.selectByPrimaryKey(csAirManual.getCardTypeId());
		return cardType.getTypeId();
	}

}
