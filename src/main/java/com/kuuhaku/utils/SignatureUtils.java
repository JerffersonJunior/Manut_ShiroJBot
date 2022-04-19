package com.kuuhaku.utils;

import com.kuuhaku.exceptions.InvalidSignatureException;
import com.kuuhaku.interfaces.Executable;
import com.kuuhaku.interfaces.annotations.Signature;
import com.kuuhaku.model.enums.I18N;
import com.kuuhaku.model.records.FailedSignature;
import com.kuuhaku.utils.json.JSONObject;
import org.intellij.lang.annotations.Language;

import java.util.*;
import java.util.regex.Pattern;

public abstract class SignatureUtils {
	@Language("RegExp") //TODO Nome deve ser pego do I18N
	private static final String ARGUMENT_PATTERN = "^<(?<name>[A-Za-z]\\w*):(?<type>[A-Za-z]+)(?<required>:[Rr])?>(?:\\[(?<options>[\\w\\-;,]+)+])?$";

	public static JSONObject parse(I18N locale, Executable exec, String input) throws InvalidSignatureException {
		JSONObject out = new JSONObject();
		List<FailedSignature> failed = new ArrayList<>();
		Signature annot = exec.getClass().getDeclaredAnnotation(Signature.class);
		if (annot == null) return out;

		String[] signatures = annot.value();

		boolean fail;
		List<String> supplied = new ArrayList<>();
		for (String sig : signatures) {
			fail = false;
			String str = input;
			String[] args = sig.split(" +");
			String[] failOpts = new String[0];

			int i = 0;
			for (String arg : args) {
				i++;
				Map<String, String> groups = Utils.extractNamedGroups(arg, ARGUMENT_PATTERN);
				String name = groups.get("name");
				boolean required = groups.containsKey("required");
				String wrap = required ? "[%s]" : "%s";

				try {
					Signature.Type type = Signature.Type.valueOf(groups.get("type").toUpperCase(Locale.ROOT));

					if (type == Signature.Type.TEXT) {
						if (str.isBlank() && required) {
							fail = true;
							supplied.add(wrap.formatted(Utils.underline(locale.get("signature/" + name))));
							continue;
						}

						if (i == args.length) {
							out.put(name, str.replaceFirst("\"(.*)\"", "$1"));
							str = "";
						} else {
							out.put(name, Utils.extract(str, type.getRegex(), "text"));
							str = str.replaceFirst(type.getRegex(), "").trim();
						}
					} else {
						List<String> opts = Arrays.stream(groups.getOrDefault("options", "").split(","))
								.filter(s -> !s.isBlank())
								.map(String::toLowerCase)
								.toList();

						String s = str.split("\s+")[0].trim();
						str = str.replaceFirst(Pattern.quote(s), "").trim();

						if (type.validate(s) && (opts.isEmpty() || opts.contains(s.toLowerCase(Locale.ROOT)))) {
							switch (type) {
								case CHANNEL -> s = s.replaceAll("[<#>]", "");
								case USER, ROLE -> s = s.replaceAll("[<@!>]", "");
							}

							if (!fail) out.put(name, s);
							supplied.add(s);
						} else if (required) {
							fail = true;
							supplied.add(wrap.formatted(Utils.underline(locale.get("signature/" + name))));
							failOpts = opts.stream().map(o -> "`" + o + "`").toArray(String[]::new);
						}
					}
				} catch (IllegalArgumentException e) {
					if (required) {
						fail = true;
						supplied.add(wrap.formatted(Utils.underline(locale.get("signature/" + name))));
					}
				}
			}

			if (fail) {
				out.clear();
				failed.add(new FailedSignature(String.join(" ", supplied), failOpts));
				supplied.clear();
			} else return out;
		}

		if (annot.allowEmpty()) return new JSONObject();
		else {
			FailedSignature first = failed.get(0);
			throw new InvalidSignatureException(first.line(), first.options());
		}
	}
}
