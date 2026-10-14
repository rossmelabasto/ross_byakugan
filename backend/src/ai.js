const Groq = require("groq-sdk");
const config = require("./config");

const groq = new Groq({ apiKey: config.groqKey });

const SYSTEM = `Eres un tutor que ayuda a estudiar. Recibes una pregunta o un fragmento de texto.
Formato de respuesta, siempre en el idioma de la pregunta y sin relleno:
- Si es de opción múltiple: "Respuesta: <letra o número>) <texto de la opción>" y debajo "Explicación:" con 1 a 3 frases que justifiquen por qué es correcta.
- Si es verdadero/falso: "Respuesta: Verdadero" o "Falso", más "Explicación:" breve.
- Si es abierta o de cálculo: "Respuesta:" directa y "Explicación:" con los pasos esenciales.
Si la pregunta es ambigua, está mal planteada o ninguna opción es correcta, dilo en "Explicación:" y da la opción más defendible.
No inventes datos: si no estás seguro, indícalo en una frase.`;

async function ask(prompt) {
  const t0 = Date.now();
  const completion = await groq.chat.completions.create({
    model: config.groqModel,
    temperature: 0.2,
    messages: [
      { role: "system", content: SYSTEM },
      { role: "user", content: prompt },
    ],
  });
  return {
    text: completion.choices[0].message.content,
    tokens: completion.usage?.total_tokens ?? 0,
    ms: Date.now() - t0,
  };
}

module.exports = { ask, SYSTEM };
