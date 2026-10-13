package it.evadid.workbook.model.feedback

/** Captured from the client-owned codecs before relocation; do not regenerate from the new writers. */
private[feedback] object FeedbackLegacyWireFixtures {
  val json: Map[String, String] = Map(
    "router" -> """["Heuristic","Ml"]""",
    "runStatus" -> """["Success","Failed","RuntimeError"]""",
    "testStatus" -> """["Passed","Failed","Errored"]""",
    "feedbackStatus" -> """["NOT_STARTET","IN_PROGRESS","FINISHED"]""",
    "config" -> """{"visibleTests":[{"name":"visible","code":"assert True","hintDE":"Hinweis"}],"hiddenTests":[{"name":"hidden","code":"assert 1"}],"fixtures":[{"path":"data.txt","content":"学校"}],"routerMode":"Ml"}""",
    "request" -> """{"exerciseText":{"$type":"LanguageMapUniversal","returnString":"Exercise"},"studentCodePython":{"$type":"BeSequence","body":[{"$type":"BeFunctionCall","funcDef":{"inputs":[{"name":{"$type":"BeEntityNamePartsBased","languageMapWithPartsString":{"$type":"LanguageMapUniversal","returnString":"value"}},"variableType":"Singleton(AnyType$)"}],"outputs":{"name":{"$type":"BeEntityNamePartsBased","languageMapWithPartsString":{"$type":"LanguageMapUniversal","returnString":"result"}},"variableType":"{\"name\":\"Unit\"}"},"body":{"body":[],"sequenceInfo":{"mustEvaluateTo":null}},"functionTypeInfo":{"isMethodInClass":null,"isNamed":{"$type":"BeEntityNamePartsBased","languageMapWithPartsString":{"$type":"LanguageMapUniversal","returnString":"print"}},"funcType":{"$type":"Function"}}},"parameterValueMap":[[{"name":{"$type":"BeEntityNamePartsBased","languageMapWithPartsString":{"$type":"LanguageMapUniversal","returnString":"value"}},"variableType":"Singleton(AnyType$)"},{"$type":"BeUseValue","value":{"$type":"BeDataValueLiteral","literalString":"1"},"contextIfKnown":null}]]}],"sequenceInfo":{"mustEvaluateTo":null}},"pythonSourceOverride":"print(1)\r\n","submissionNr":2,"config":{"visibleTests":[{"name":"visible","code":"assert True","hintDE":"Hinweis"}],"hiddenTests":[{"name":"hidden","code":"assert 1"}],"fixtures":[{"path":"data.txt","content":"学校"}],"routerMode":"Ml"},"meta":{"exerciseId":"exercise","userId":"user"}}""",
    "outcome" -> """{"tests":[{"name":"test","passed":false,"expected":"1","actual":"2","message":"Mismatch"}],"runStatus":"Failed","normalizedScore":0.5,"runtimeError":null,"stdout":"out","stderr":"err"}""",
    "feedback" -> """{"summary":"Es liegen noch keine Analysen vor.","tests":[{"name":"test","passed":false,"expected":"1","actual":"2","message":"Mismatch"}],"generalHints":[],"displayTests":[{"name":"test","passed":false,"message":"Mismatch","expectedActual":{"expectedLabel":"Expected","expected":"1","actualLabel":"Actual","actual":"2"}}],"rawPython":"print(1)","status":"FINISHED","normalizedScore":0,"timestampEpochMillis":123456789}""",
    "runtimeRequest" -> """{"code":"print(1)","visibleTests":[{"name":"test","code":"assert True"}],"hiddenTests":[],"fixtures":[{"path":"data.txt","content":"学校"}],"packages":["math"],"timeoutMs":5000}""",
    "runtimeResult" -> """{"status":"Failed","tests":[{"name":"test","status":"Failed","isHidden":false,"message":"Mismatch","durationMs":2.5,"hint":"hint"}],"stdout":"out","stderr":"err","error":null,"score":0.5}"""
  )
}
