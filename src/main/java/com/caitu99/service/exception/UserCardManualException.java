package com.caitu99.service.exception;


/**
 * 用户手动查询积分接口异常
 * @author 熊斌 2015年11月10日
 */
public class UserCardManualException extends ApiException {
	
	private static final long serialVersionUID = 1537901235563361521L;

	public UserCardManualException(int code, String description) {
		super(code, description);
	}
	
	public UserCardManualException(int code, String description, String data) {
		super(code, description, data);
	}
	
}
