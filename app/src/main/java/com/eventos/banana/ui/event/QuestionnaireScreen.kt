package com.eventos.banana.ui.event

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventos.banana.domain.model.Event

@Composable
fun QuestionnaireScreen(
    event: Event,
    onSubmit: (Map<String, String>) -> Unit,
    onCancel: () -> Unit
) {
    val answers = remember { mutableStateMapOf<String, String>() }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        Text(
            text = "Solicitud para: ${event.title}",
            style = MaterialTheme.typography.titleLarge
        )

        event.joinQuestions.forEach { question ->
            OutlinedTextField(
                value = answers[question.id] ?: "",
                onValueChange = { answers[question.id] = it },
                label = {
                    Text(
                        if (question.required)
                            "${question.text} *"
                        else
                            question.text
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val missingRequired = event.joinQuestions.any {
                    it.required && answers[it.id].isNullOrBlank()
                }

                if (missingRequired) {
                    error = "Responde todas las preguntas obligatorias"
                    return@Button
                }

                onSubmit(answers.toMap())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Enviar solicitud")
        }

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cancelar")
        }
    }
}
