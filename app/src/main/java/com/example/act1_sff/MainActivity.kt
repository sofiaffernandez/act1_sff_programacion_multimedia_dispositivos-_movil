package com.example.act1_sff

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale


val GRUPOS = listOf(
    "Grupo 1 – Ingenieros y licenciados",
    "Grupo 2 – Ingenieros técnicos, peritos y ayudantes titulados",
    "Grupo 3 – Jefes administrativos y de taller",
    "Grupo 4 – Ayudantes no titulados",
    "Grupo 5 – Oficiales administrativos",
    "Grupo 6 – Subalternos",
    "Grupo 7 – Auxiliares administrativos"
)

val BASE_MINIMA = listOf(23000.0, 19000.0, 16500.0, 16000.0, 15500.0, 15000.0, 15000.0)
const val BASE_MAXIMA = 58000.0

val DISCAPACIDAD = listOf("Ninguno", "Entre el 33 % y el 65 %", "65 % o más")
val ESTADOS_CIVILES = listOf("Soltero/a", "Casado/a", "Divorciado/a", "Viudo/a")
val PAGAS = listOf(12, 14)

val TRAMOS_IRPF = listOf(
    12450.0 to 0.19,
    20200.0 to 0.24,
    35200.0 to 0.30,
    60000.0 to 0.37,
    300000.0 to 0.45,
    Double.MAX_VALUE to 0.47
)

const val TIPO_SEGURIDAD_SOCIAL = 0.0635

enum class Pantalla { DATOS, RESULTADOS }

data class DatosSalario(
    val salarioBruto: Double,
    val edad: Int,
    val grupo: Int,
    val discapacidad: Int,
    val estadoCivil: Int,
    val hijos: Int,
    val pagas: Int
)

data class Deduccion(val concepto: String, val importe: Double)

data class ResultadoSalario(
    val bruto: Double,
    val pagas: Int,
    val seguridadSocial: Double,
    val irpf: Double,
    val tipoIrpf: Double,
    val deducciones: List<Deduccion>,
    val neto: Double
)

class FormularioState {
    var bruto by mutableStateOf("")
    var edad by mutableStateOf("")
    var hijos by mutableStateOf("0")
    var grupo by mutableStateOf(0)
    var discapacidad by mutableStateOf(0)
    var estadoCivil by mutableStateOf(0)
    var pagas by mutableStateOf(0)
}


fun aplicarTarifa(base: Double): Double {
    var anterior = 0.0
    var cuota = 0.0
    for ((limite, tipo) in TRAMOS_IRPF) {
        if (base <= anterior) break
        val tramo = minOf(base, limite) - anterior
        cuota += tramo * tipo
        anterior = limite
    }
    return cuota
}

fun calcularSalario(d: DatosSalario): ResultadoSalario {
    val bruto = d.salarioBruto

    // 1) Seguridad Social: el grupo profesional marca la base mínima de cotización
    val baseCotizacion = bruto.coerceIn(BASE_MINIMA[d.grupo], BASE_MAXIMA)
    val seguridadSocial = minOf(baseCotizacion * TIPO_SEGURIDAD_SOCIAL, bruto)

    // 2) Reducciones de la base del IRPF
    val deducciones = mutableListOf<Deduccion>()
    val gastos = 2000.0
    deducciones += Deduccion("Gastos deducibles del trabajo", gastos)

    val reduccionDiscapacidad = when (d.discapacidad) {
        1 -> 3000.0
        2 -> 7750.0
        else -> 0.0
    }
    if (reduccionDiscapacidad > 0) {
        deducciones += Deduccion("Reducción por discapacidad del trabajador", reduccionDiscapacidad)
    }

    // Soltero, divorciado o viudo con hijos: unidad familiar monoparental
    val monoparental = d.hijos > 0 && ESTADOS_CIVILES[d.estadoCivil] != "Casado/a"
    val reduccionMonoparental = if (monoparental) 2150.0 else 0.0
    if (monoparental) {
        deducciones += Deduccion("Reducción por familia monoparental", reduccionMonoparental)
    }

    val baseIrpf = maxOf(0.0, bruto - seguridadSocial - gastos - reduccionDiscapacidad - reduccionMonoparental)

    // minimos personal y familiar (se restan de la cuota)
    var minimoPersonal = 5550.0
    if (d.edad >= 65) minimoPersonal += 1150.0
    if (d.edad >= 75) minimoPersonal += 1400.0
    deducciones += Deduccion("Mínimo personal (según edad)", minimoPersonal)

    val minimoHijos = (1..d.hijos).sumOf { n ->
        when (n) {
            1 -> 2400.0
            2 -> 2700.0
            3 -> 4000.0
            else -> 4500.0
        }
    }
    if (minimoHijos > 0) {
        deducciones += Deduccion("Mínimo por descendientes", minimoHijos)
    }

    // Cuota del IRPF = tarifa(base) - tarifa(mínimos)
    val cuotaIrpf = maxOf(0.0, aplicarTarifa(baseIrpf) - aplicarTarifa(minimoPersonal + minimoHijos))
    val irpf = minOf(cuotaIrpf, bruto - seguridadSocial)
    val tipoIrpf = if (bruto > 0) irpf / bruto * 100 else 0.0

    val neto = bruto - seguridadSocial - irpf

    return ResultadoSalario(
        bruto = bruto,
        pagas = d.pagas,
        seguridadSocial = seguridadSocial,
        irpf = irpf,
        tipoIrpf = tipoIrpf,
        deducciones = deducciones,
        neto = neto
    )
}

fun euros(valor: Double): String =
    String.format(Locale.forLanguageTag("es-ES"), "%,.2f €", valor)


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppSalario()
            }
        }
    }
}

@Composable
fun AppSalario() {
    var pantalla by rememberSaveable { mutableStateOf(Pantalla.DATOS) }
    var resultado by remember { mutableStateOf<ResultadoSalario?>(null) }
    val formulario = remember { FormularioState() }

    Scaffold { innerPadding ->
        val modificador = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
        val res = resultado

        if (pantalla == Pantalla.RESULTADOS && res != null) {
            PantallaResultados(
                resultado = res,
                onNuevaSimulacion = { pantalla = Pantalla.DATOS },
                modifier = modificador
            )
        } else {
            PantallaDatos(
                formulario = formulario,
                onCalcular = {
                    resultado = it
                    pantalla = Pantalla.RESULTADOS
                },
                modifier = modificador
            )
        }
    }
}


@Composable
fun PantallaDatos(
    formulario: FormularioState,
    onCalcular: (ResultadoSalario) -> Unit,
    modifier: Modifier = Modifier
) {
    var error by remember { mutableStateOf("") }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Calculadora de salario neto", style = MaterialTheme.typography.headlineMedium)
        Text("Introduce tus datos para simular el salario neto.")

        OutlinedTextField(
            value = formulario.bruto,
            onValueChange = { formulario.bruto = it },
            label = { Text("Salario bruto anual (€)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = formulario.edad,
            onValueChange = { formulario.edad = it },
            label = { Text("Edad") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Selector(
            titulo = "Grupo profesional",
            opciones = GRUPOS,
            seleccionada = formulario.grupo,
            onSeleccion = { formulario.grupo = it }
        )

        Selector(
            titulo = "Estado civil",
            opciones = ESTADOS_CIVILES,
            seleccionada = formulario.estadoCivil,
            onSeleccion = { formulario.estadoCivil = it }
        )

        OutlinedTextField(
            value = formulario.hijos,
            onValueChange = { formulario.hijos = it },
            label = { Text("Número de hijos") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Selector(
            titulo = "Grado de discapacidad",
            opciones = DISCAPACIDAD,
            seleccionada = formulario.discapacidad,
            onSeleccion = { formulario.discapacidad = it }
        )

        Selector(
            titulo = "Número de pagas",
            opciones = PAGAS.map { "$it pagas" },
            seleccionada = formulario.pagas,
            onSeleccion = { formulario.pagas = it }
        )

        if (error.isNotEmpty()) {
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = {
                val bruto = formulario.bruto.replace(',', '.').toDoubleOrNull() ?: 0.0
                val edad = formulario.edad.toIntOrNull() ?: -1
                val hijos = formulario.hijos.toIntOrNull() ?: -1

                when {
                    bruto <= 0.0 -> error = "Introduce un salario bruto anual válido"
                    edad !in 16..99 -> error = "La edad debe estar entre 16 y 99 años"
                    hijos !in 0..15 -> error = "El número de hijos debe estar entre 0 y 15"
                    else -> {
                        error = ""
                        onCalcular(
                            calcularSalario(
                                DatosSalario(
                                    salarioBruto = bruto,
                                    edad = edad,
                                    grupo = formulario.grupo,
                                    discapacidad = formulario.discapacidad,
                                    estadoCivil = formulario.estadoCivil,
                                    hijos = hijos,
                                    pagas = PAGAS[formulario.pagas]
                                )
                            )
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Calcular salario neto")
        }
    }
}


@Composable
fun PantallaResultados(
    resultado: ResultadoSalario,
    onNuevaSimulacion: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Resultados", style = MaterialTheme.typography.headlineMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Fila("Salario bruto anual", euros(resultado.bruto))
                Fila("Bruto por paga", euros(resultado.bruto / resultado.pagas))
                Spacer(modifier = Modifier.height(8.dp))
                Fila("Seguridad Social", euros(resultado.seguridadSocial))
                Fila(
                    "Retención de IRPF",
                    "${euros(resultado.irpf)} (${String.format(Locale.forLanguageTag("es-ES"), "%.2f", resultado.tipoIrpf)} %)"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Fila("Salario neto anual", euros(resultado.neto), destacado = true)
                Fila(
                    "Neto por paga (${resultado.pagas} pagas)",
                    euros(resultado.neto / resultado.pagas),
                    destacado = true
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Posibles deducciones", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                resultado.deducciones.forEach { deduccion ->
                    Fila(deduccion.concepto, euros(deduccion.importe))
                }
            }
        }

        Text(
            "Cálculo simulado con fines didácticos: no equivale a una nómina real.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(onClick = onNuevaSimulacion, modifier = Modifier.fillMaxWidth()) {
            Text("Nueva simulación")
        }
    }
}

@Composable
fun Fila(etiqueta: String, valor: String, destacado: Boolean = false) {
    val peso = if (destacado) FontWeight.Bold else FontWeight.Normal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = etiqueta, fontWeight = peso, modifier = Modifier.weight(1f))
        Text(text = valor, fontWeight = peso)
    }
}

@Composable
fun Selector(
    titulo: String,
    opciones: List<String>,
    seleccionada: Int,
    onSeleccion: (Int) -> Unit
) {
    var abierto by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(titulo, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(
                onClick = { abierto = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(opciones[seleccionada], modifier = Modifier.weight(1f))
                Text("▼")
            }
            DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
                opciones.forEachIndexed { indice, opcion ->
                    DropdownMenuItem(
                        text = { Text(opcion) },
                        onClick = {
                            onSeleccion(indice)
                            abierto = false
                        }
                    )
                }
            }
        }
    }
}