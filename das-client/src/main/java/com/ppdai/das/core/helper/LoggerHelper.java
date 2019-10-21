package com.ppdai.das.core.helper;

import java.io.PrintWriter;
import java.io.StringWriter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ppdai.das.core.EventEnum;
import com.ppdai.das.core.LogEntry;

public class LoggerHelper {

	public static final String SQLHIDDENString = "*";

	private static class ObjectMapperHolder{
		private static ObjectMapper objectMapperInstance=new ObjectMapper();
		static {
			objectMapperInstance.configure(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
			objectMapperInstance.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
		}
	}

	public static ObjectMapper getObjectMapperInstance(){
		return ObjectMapperHolder.objectMapperInstance;
	}

	public static int getHashCode(String str) {
		str = getCompactSql(str);
		int hash, i;
		char[] arr = str.toCharArray();
		for (hash = i = 0; i < arr.length; ++i) {
			hash += arr[i];
			hash += (hash << 12);
			hash ^= (hash >> 4);
		}
		hash += (hash << 3);
		hash ^= (hash >> 11);
		hash += (hash << 15);
		return hash;
	}
	
	public static String getCompactSql(String sql) {
		StringBuffer sqlnew=new StringBuffer();
		StringBuffer word=new StringBuffer();
		char[] chrs = sql.toCharArray(); // sql字符拆到数组里
		int len = chrs.length;

		// 遍历sql字符数组
		for (int i = 0; i < len; i++) {
			char chr = chrs[i];

			// 对单引号的处理（对 单引号 和 操作符 是分两个逻辑处理）
			if (chr == '\'') {
				for (i++; i < len; i++) {
					// 如果不等于'，continue
					if (chrs[i] != '\'')
						continue;

					// 循环到结尾，break
					if (i == len - 1)
						break;

					// 如果下一字符是'，继续
					if (chrs[i + 1] == '\'')
						i++;
					else
						break;
				}
				continue;
			}

			// 过滤字符
			if ((chr >= 97 && chr <= 122)) {
				// 普通字符，加入word流
				word.append(chr);
				continue;
			} else {
				// 对断词符号的处理 right(a.name,8)
				if (chr == ' ' || chr == ',' || chr == '(' || chr == ')'
						|| chr == '[' || chr == ']' || chr == '=' || chr == '<'
						|| chr == '>' || chr == '\t' || chr == '\r'
						|| chr == '\n' || chr == '`' || chr == '!'
						|| chr == '+' || chr == '-' || chr == '*' || chr == '/'
						|| chr == '%' || chr == '&' || chr == '|' || chr == '^') {
					// 如果保留该符号，则进行处理
					if (chr == '=' || chr == '<' || chr == '>' || chr == '!') {
						word.append(chr);
						sqlnew.append(checkWord(word.toString()));
						word.setLength(0);
					} else {
						// 断词符号不在保留范围内，则丢弃，并将之前的word流加入sqlnew
						if (word.length() > 0) {
							sqlnew.append(checkWord(word.toString()));
							word.setLength(0);
						}
					}

					continue;
				}

				// 普通字符，加入word流
				word.append(chr);
			}
		}

		if (word.length() > 0) {
			sqlnew.append(checkWord(word.toString()));
		}

		return sqlnew.toString();
	}

	public static String checkWord(String word) {
		char[] chrs = word.toCharArray();
		char chr = chrs[0];
		if (chr == '@' || chr == '$') {
			return "";
		}
		if (chr >= '0' && chr <= '9') {
			return "";
		}
		return word;
	}
	
	public static String getSqlTpl(LogEntry entry) {
		if ( entry.isSensitive() )
			return SQLHIDDENString;
		EventEnum event = entry.getEvent();
		
		if(event == EventEnum.QUERY ||  event == EventEnum.UPDATE_SIMPLE ||
				event == EventEnum.UPDATE_KH || event == EventEnum.BATCH_UPDATE_PARAM){
			return entry.getSqls() != null && entry.getSqls().length > 0 ? entry.getSqls()[0] : "";
		}
		if(event == EventEnum.BATCH_UPDATE){
			return join(entry.getSqls(), ";");
		}
		if(event == EventEnum.CALL || event == EventEnum.BATCH_CALL){
			return entry.getCallString();
		}
		
		return "";
	}
	
	public static String getParams(LogEntry entry) {
		EventEnum event = entry.getEvent();
		String[] pramemters = entry.getPramemters();
		
		StringBuilder sbout = new StringBuilder();
		if(pramemters == null || pramemters.length <= 0){
			return sbout.toString();
		}
		if(event == EventEnum.QUERY || 
				event == EventEnum.UPDATE_SIMPLE ||
				event == EventEnum.UPDATE_KH ||
				event == EventEnum.CALL){
			return null != pramemters && pramemters.length > 0 ? pramemters[0] : "";
		}
		if(event == EventEnum.BATCH_UPDATE_PARAM ||
				event == EventEnum.BATCH_CALL){
			for(String param : pramemters){
				sbout.append(param + ";");
			}
			return sbout.substring(0, sbout.length() - 1);
		}
		return "";
	}
	
	public static String join(Object[] array, String separator) {
		if (array == null) {
			return null;
		}
		return join(array, separator, 0, array.length);
	}

	public static String join(Object[] array, String separator, int startIndex, int endIndex) {
		if (array == null) {
			return null;
		}
		if (separator == null) {
			separator = "";
		}

		int noOfItems = endIndex - startIndex;
		if (noOfItems <= 0) {
			return "";
		}

		StringBuilder buf = new StringBuilder(noOfItems * 16);

		for (int i = startIndex; i < endIndex; ++i) {
			if (i > startIndex) {
				buf.append(separator);
			}
			if (array[i] != null) {
				buf.append(array[i]);
			}
		}
		return buf.toString();
	}
	
	public static String getExceptionStack(Throwable e) {
		String msg = e.getMessage();
		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			msg = sw.toString();
		} catch (Throwable e2) {
			msg = "bad getErrorInfoFromException";
		}

		return msg;
	}

	public static String toJson(Object object) {
		try {
			return getObjectMapperInstance().writeValueAsString(object);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "Error convert log value to json string!";
	}
}