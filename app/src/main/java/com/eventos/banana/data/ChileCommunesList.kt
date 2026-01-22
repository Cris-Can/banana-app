package com.eventos.banana.data

/**
 * Lista de comunas de Chile organizadas por región
 * Usada para selección en registro de usuario y filtros
 */
object ChileCommunesList {
    
    // Mapa de Comuna -> Región
    private val communeRegionMap = mapOf(
        // Arica y Parinacota
        "Arica" to "Región de Arica y Parinacota",
        "Camarones" to "Región de Arica y Parinacota",
        "Putre" to "Región de Arica y Parinacota",
        "General Lagos" to "Región de Arica y Parinacota",

        // Tarapacá
        "Iquique" to "Región de Tarapacá",
        "Alto Hospicio" to "Región de Tarapacá",
        "Pozo Almonte" to "Región de Tarapacá",
        "Camiña" to "Región de Tarapacá",
        "Colchane" to "Región de Tarapacá",
        "Huara" to "Región de Tarapacá",
        "Pica" to "Región de Tarapacá",

        // Antofagasta
        "Antofagasta" to "Región de Antofagasta",
        "Mejillones" to "Región de Antofagasta",
        "Sierra Gorda" to "Región de Antofagasta",
        "Taltal" to "Región de Antofagasta",
        "Calama" to "Región de Antofagasta",
        "Ollagüe" to "Región de Antofagasta",
        "San Pedro de Atacama" to "Región de Antofagasta",
        "Tocopilla" to "Región de Antofagasta",
        "María Elena" to "Región de Antofagasta",

        // Atacama
        "Copiapó" to "Región de Atacama",
        "Caldera" to "Región de Atacama",
        "Tierra Amarilla" to "Región de Atacama",
        "Chañaral" to "Región de Atacama",
        "Diego de Almagro" to "Región de Atacama",
        "Vallenar" to "Región de Atacama",
        "Alto del Carmen" to "Región de Atacama",
        "Freirina" to "Región de Atacama",
        "Huasco" to "Región de Atacama",

        // Coquimbo
        "La Serena" to "Región de Coquimbo",
        "Coquimbo" to "Región de Coquimbo",
        "Andacollo" to "Región de Coquimbo",
        "La Higuera" to "Región de Coquimbo",
        "Paiguano" to "Región de Coquimbo",
        "Vicuña" to "Región de Coquimbo",
        "Illapel" to "Región de Coquimbo",
        "Canela" to "Región de Coquimbo",
        "Los Vilos" to "Región de Coquimbo",
        "Salamanca" to "Región de Coquimbo",
        "Ovalle" to "Región de Coquimbo",
        "Combarbalá" to "Región de Coquimbo",
        "Monte Patria" to "Región de Coquimbo",
        "Punitaqui" to "Región de Coquimbo",
        "Río Hurtado" to "Región de Coquimbo",

        // Valparaíso
        "Valparaíso" to "Región de Valparaíso",
        "Casablanca" to "Región de Valparaíso",
        "Concón" to "Región de Valparaíso",
        "Juan Fernández" to "Región de Valparaíso",
        "Puchuncaví" to "Región de Valparaíso",
        "Quintero" to "Región de Valparaíso",
        "Viña del Mar" to "Región de Valparaíso",
        "Isla de Pascua" to "Región de Valparaíso",
        "Los Andes" to "Región de Valparaíso",
        "Calle Larga" to "Región de Valparaíso",
        "Rinconada" to "Región de Valparaíso",
        "San Esteban" to "Región de Valparaíso",
        "La Ligua" to "Región de Valparaíso",
        "Cabildo" to "Región de Valparaíso",
        "Papudo" to "Región de Valparaíso",
        "Petorca" to "Región de Valparaíso",
        "Zapallar" to "Región de Valparaíso",
        "Quillota" to "Región de Valparaíso",
        "Calera" to "Región de Valparaíso",
        "Hijuelas" to "Región de Valparaíso",
        "La Cruz" to "Región de Valparaíso",
        "Nogales" to "Región de Valparaíso",
        "San Antonio" to "Región de Valparaíso",
        "Algarrobo" to "Región de Valparaíso",
        "Cartagena" to "Región de Valparaíso",
        "El Quisco" to "Región de Valparaíso",
        "El Tabo" to "Región de Valparaíso",
        "Santo Domingo" to "Región de Valparaíso",
        "San Felipe" to "Región de Valparaíso",
        "Catemu" to "Región de Valparaíso",
        "Llaillay" to "Región de Valparaíso",
        "Panquehue" to "Región de Valparaíso",
        "Putaendo" to "Región de Valparaíso",
        "Santa María" to "Región de Valparaíso",
        "Quilpué" to "Región de Valparaíso",
        "Limache" to "Región de Valparaíso",
        "Olmué" to "Región de Valparaíso",
        "Villa Alemana" to "Región de Valparaíso",

        // Región Metropolitana
        "Cerrillos" to "Región Metropolitana",
        "Cerro Navia" to "Región Metropolitana",
        "Conchalí" to "Región Metropolitana",
        "El Bosque" to "Región Metropolitana",
        "Estación Central" to "Región Metropolitana",
        "Huechuraba" to "Región Metropolitana",
        "Independencia" to "Región Metropolitana",
        "La Cisterna" to "Región Metropolitana",
        "La Florida" to "Región Metropolitana",
        "La Granja" to "Región Metropolitana",
        "La Pintana" to "Región Metropolitana",
        "La Reina" to "Región Metropolitana",
        "Las Condes" to "Región Metropolitana",
        "Lo Barnechea" to "Región Metropolitana",
        "Lo Espejo" to "Región Metropolitana",
        "Lo Prado" to "Región Metropolitana",
        "Macul" to "Región Metropolitana",
        "Maipú" to "Región Metropolitana",
        "Ñuñoa" to "Región Metropolitana",
        "Pedro Aguirre Cerda" to "Región Metropolitana",
        "Peñalolén" to "Región Metropolitana",
        "Providencia" to "Región Metropolitana",
        "Pudahuel" to "Región Metropolitana",
        "Puente Alto" to "Región Metropolitana",
        "Quilicura" to "Región Metropolitana",
        "Quinta Normal" to "Región Metropolitana",
        "Recoleta" to "Región Metropolitana",
        "Renca" to "Región Metropolitana",
        "San Joaquín" to "Región Metropolitana",
        "San Miguel" to "Región Metropolitana",
        "San Ramón" to "Región Metropolitana",
        "Santiago" to "Región Metropolitana",
        "Vitacura" to "Región Metropolitana",
        "San Bernardo" to "Región Metropolitana",
        "Buin" to "Región Metropolitana",
        "Calera de Tango" to "Región Metropolitana",
        "Paine" to "Región Metropolitana",
        "Pirque" to "Región Metropolitana",
        "San José de Maipo" to "Región Metropolitana",
        "Colina" to "Región Metropolitana",
        "Lampa" to "Región Metropolitana",
        "Tiltil" to "Región Metropolitana",
        "Melipilla" to "Región Metropolitana",
        "Alhué" to "Región Metropolitana",
        "Curacaví" to "Región Metropolitana",
        "María Pinto" to "Región Metropolitana",
        "San Pedro" to "Región Metropolitana",
        "Talagante" to "Región Metropolitana",
        "El Monte" to "Región Metropolitana",
        "Isla de Maipo" to "Región Metropolitana",
        "Padre Hurtado" to "Región Metropolitana",
        "Peñaflor" to "Región Metropolitana",

        // O'Higgins
        "Rancagua" to "Región de O'Higgins",
        "Codegua" to "Región de O'Higgins",
        "Coinco" to "Región de O'Higgins",
        "Coltauco" to "Región de O'Higgins",
        "Doñihue" to "Región de O'Higgins",
        "Graneros" to "Región de O'Higgins",
        "Las Cabras" to "Región de O'Higgins",
        "Machalí" to "Región de O'Higgins",
        "Malloa" to "Región de O'Higgins",
        "Mostazal" to "Región de O'Higgins",
        "Olivar" to "Región de O'Higgins",
        "Peumo" to "Región de O'Higgins",
        "Pichidegua" to "Región de O'Higgins",
        "Quinta de Tilcoco" to "Región de O'Higgins",
        "Rengo" to "Región de O'Higgins",
        "Requínoa" to "Región de O'Higgins",
        "San Vicente" to "Región de O'Higgins",
        "Pichilemu" to "Región de O'Higgins",
        "La Estrella" to "Región de O'Higgins",
        "Litueche" to "Región de O'Higgins",
        "Marchihue" to "Región de O'Higgins",
        "Navidad" to "Región de O'Higgins",
        "Paredones" to "Región de O'Higgins",
        "San Fernando" to "Región de O'Higgins",
        "Chépica" to "Región de O'Higgins",
        "Chimbarongo" to "Región de O'Higgins",
        "Lolol" to "Región de O'Higgins",
        "Nancagua" to "Región de O'Higgins",
        "Palmilla" to "Región de O'Higgins",
        "Peralillo" to "Región de O'Higgins",
        "Placilla" to "Región de O'Higgins",
        "Pumanque" to "Región de O'Higgins",
        "Santa Cruz" to "Región de O'Higgins",

        // Maule
        "Talca" to "Región del Maule",
        "Constitución" to "Región del Maule",
        "Curepto" to "Región del Maule",
        "Empedrado" to "Región del Maule",
        "Maule" to "Región del Maule",
        "Pelarco" to "Región del Maule",
        "Pencahue" to "Región del Maule",
        "Río Claro" to "Región del Maule",
        "San Clemente" to "Región del Maule",
        "San Rafael" to "Región del Maule",
        "Cauquenes" to "Región del Maule",
        "Chanco" to "Región del Maule",
        "Pelluhue" to "Región del Maule",
        "Curicó" to "Región del Maule",
        "Hualañé" to "Región del Maule",
        "Licantén" to "Región del Maule",
        "Molina" to "Región del Maule",
        "Rauco" to "Región del Maule",
        "Romeral" to "Región del Maule",
        "Sagrada Familia" to "Región del Maule",
        "Teno" to "Región del Maule",
        "Vichuquén" to "Región del Maule",
        "Linares" to "Región del Maule",
        "Colbún" to "Región del Maule",
        "Longaví" to "Región del Maule",
        "Parral" to "Región del Maule",
        "Retiro" to "Región del Maule",
        "San Javier" to "Región del Maule",
        "Villa Alegre" to "Región del Maule",
        "Yerbas Buenas" to "Región del Maule",

        // Ñuble
        "Chillán" to "Región de Ñuble",
        "Bulnes" to "Región de Ñuble",
        "Chillán Viejo" to "Región de Ñuble",
        "El Carmen" to "Región de Ñuble",
        "Pemuco" to "Región de Ñuble",
        "Pinto" to "Región de Ñuble",
        "Quillón" to "Región de Ñuble",
        "San Ignacio" to "Región de Ñuble",
        "Yungay" to "Región de Ñuble",
        "Quirihue" to "Región de Ñuble",
        "Cobquecura" to "Región de Ñuble",
        "Coelemu" to "Región de Ñuble",
        "Ninhue" to "Región de Ñuble",
        "Portezuelo" to "Región de Ñuble",
        "Ránquil" to "Región de Ñuble",
        "Treguaco" to "Región de Ñuble",
        "San Carlos" to "Región de Ñuble",
        "Coihueco" to "Región de Ñuble",
        "Ñiquén" to "Región de Ñuble",
        "San Fabián" to "Región de Ñuble",
        "San Nicolás" to "Región de Ñuble",

        // Biobío
        "Concepción" to "Región del Biobío",
        "Coronel" to "Región del Biobío",
        "Chiguayante" to "Región del Biobío",
        "Florida" to "Región del Biobío",
        "Hualpén" to "Región del Biobío",
        "Hualqui" to "Región del Biobío",
        "Lota" to "Región del Biobío",
        "Penco" to "Región del Biobío",
        "San Pedro de la Paz" to "Región del Biobío",
        "Santa Juana" to "Región del Biobío",
        "Talcahuano" to "Región del Biobío",
        "Tomé" to "Región del Biobío",
        "Lebu" to "Región del Biobío",
        "Arauco" to "Región del Biobío",
        "Cañete" to "Región del Biobío",
        "Contulmo" to "Región del Biobío",
        "Curanilahue" to "Región del Biobío",
        "Los Álamos" to "Región del Biobío",
        "Tirúa" to "Región del Biobío",
        "Los Ángeles" to "Región del Biobío",
        "Antuco" to "Región del Biobío",
        "Cabrero" to "Región del Biobío",
        "Laja" to "Región del Biobío",
        "Mulchén" to "Región del Biobío",
        "Nacimiento" to "Región del Biobío",
        "Negrete" to "Región del Biobío",
        "Quilaco" to "Región del Biobío",
        "Quilleco" to "Región del Biobío",
        "San Rosendo" to "Región del Biobío",
        "Santa Bárbara" to "Región del Biobío",
        "Tucapel" to "Región del Biobío",
        "Yumbel" to "Región del Biobío",
        "Alto Biobío" to "Región del Biobío",

        // Araucanía
        "Temuco" to "Región de La Araucanía",
        "Carahue" to "Región de La Araucanía",
        "Cunco" to "Región de La Araucanía",
        "Curarrehue" to "Región de La Araucanía",
        "Freire" to "Región de La Araucanía",
        "Galvarino" to "Región de La Araucanía",
        "Gorbea" to "Región de La Araucanía",
        "Lautaro" to "Región de La Araucanía",
        "Loncoche" to "Región de La Araucanía",
        "Melipeuco" to "Región de La Araucanía",
        "Nueva Imperial" to "Región de La Araucanía",
        "Padre Las Casas" to "Región de La Araucanía",
        "Perquenco" to "Región de La Araucanía",
        "Pitrufquén" to "Región de La Araucanía",
        "Pucón" to "Región de La Araucanía",
        "Saavedra" to "Región de La Araucanía",
        "Teodoro Schmidt" to "Región de La Araucanía",
        "Toltén" to "Región de La Araucanía",
        "Vilcún" to "Región de La Araucanía",
        "Villarrica" to "Región de La Araucanía",
        "Cholchol" to "Región de La Araucanía",
        "Angol" to "Región de La Araucanía",
        "Collipulli" to "Región de La Araucanía",
        "Curacautín" to "Región de La Araucanía",
        "Ercilla" to "Región de La Araucanía",
        "Lonquimay" to "Región de La Araucanía",
        "Los Sauces" to "Región de La Araucanía",
        "Lumaco" to "Región de La Araucanía",
        "Purén" to "Región de La Araucanía",
        "Renaico" to "Región de La Araucanía",
        "Traiguén" to "Región de La Araucanía",
        "Victoria" to "Región de La Araucanía",

        // Los Ríos
        "Valdivia" to "Región de Los Ríos",
        "Corral" to "Región de Los Ríos",
        "Lanco" to "Región de Los Ríos",
        "Los Lagos" to "Región de Los Ríos",
        "Máfil" to "Región de Los Ríos",
        "Mariquina" to "Región de Los Ríos",
        "Paillaco" to "Región de Los Ríos",
        "Panguipulli" to "Región de Los Ríos",
        "La Unión" to "Región de Los Ríos",
        "Futrono" to "Región de Los Ríos",
        "Lago Ranco" to "Región de Los Ríos",
        "Río Bueno" to "Región de Los Ríos",

        // Los Lagos
        "Puerto Montt" to "Región de Los Lagos",
        "Calbuco" to "Región de Los Lagos",
        "Cochamó" to "Región de Los Lagos",
        "Fresia" to "Región de Los Lagos",
        "Frutillar" to "Región de Los Lagos",
        "Los Muermos" to "Región de Los Lagos",
        "Llanquihue" to "Región de Los Lagos",
        "Maullín" to "Región de Los Lagos",
        "Puerto Varas" to "Región de Los Lagos",
        "Castro" to "Región de Los Lagos",
        "Ancud" to "Región de Los Lagos",
        "Chonchi" to "Región de Los Lagos",
        "Curaco de Vélez" to "Región de Los Lagos",
        "Dalcahue" to "Región de Los Lagos",
        "Puqueldón" to "Región de Los Lagos",
        "Queilén" to "Región de Los Lagos",
        "Quellón" to "Región de Los Lagos",
        "Quemchi" to "Región de Los Lagos",
        "Quinchao" to "Región de Los Lagos",
        "Osorno" to "Región de Los Lagos",
        "Puerto Octay" to "Región de Los Lagos",
        "Purranque" to "Región de Los Lagos",
        "Puyehue" to "Región de Los Lagos",
        "Río Negro" to "Región de Los Lagos",
        "San Juan de la Costa" to "Región de Los Lagos",
        "San Pablo" to "Región de Los Lagos",
        "Chaitén" to "Región de Los Lagos",
        "Futaleufú" to "Región de Los Lagos",
        "Hualaihué" to "Región de Los Lagos",
        "Palena" to "Región de Los Lagos",

        // Aysén
        "Coyhaique" to "Región de Aysén",
        "Lago Verde" to "Región de Aysén",
        "Aysén" to "Región de Aysén",
        "Cisnes" to "Región de Aysén",
        "Guaitecas" to "Región de Aysén",
        "Cochrane" to "Región de Aysén",
        "O'Higgins" to "Región de Aysén",
        "Tortel" to "Región de Aysén",
        "Chile Chico" to "Región de Aysén",
        "Río Ibáñez" to "Región de Aysén",

        // Magallanes
        "Punta Arenas" to "Región de Magallanes",
        "Laguna Blanca" to "Región de Magallanes",
        "Río Verde" to "Región de Magallanes",
        "San Gregorio" to "Región de Magallanes",
        "Cabo de Hornos" to "Región de Magallanes",
        "Antártica" to "Región de Magallanes",
        "Porvenir" to "Región de Magallanes",
        "Primavera" to "Región de Magallanes",
        "Timaukel" to "Región de Magallanes",
        "Natales" to "Región de Magallanes",
        "Torres del Paine" to "Región de Magallanes"
    )

    val communes = communeRegionMap.keys.toList().sorted() // Ordenadas alfabéticamente
    
    /**
     * Obtiene la región de una comuna
     */
    fun getRegionForCommune(commune: String): String {
        return communeRegionMap[commune] ?: "Región no definida"
    }

    /**
     * Retorna un mapa de todas las regiones con sus respectivas comunas
     * Usado por el selector de eventos
     */
    fun getRegionsWithCommunes(): Map<String, List<String>> {
        val result = mutableMapOf<String, MutableList<String>>()
        communeRegionMap.forEach { (commune, region) ->
            if (!result.containsKey(region)) {
                result[region] = mutableListOf()
            }
            result[region]?.add(commune)
        }
        // Ordenar listas
        result.forEach { (_, list) -> list.sort() }
        return result.toSortedMap()
    }
    
    /**
     * Busca alguna comuna contenida dentro de un texto largo (ej: dirección completa)
     * Prioriza la coincidencia más larga para evitar falsos positivos
     * (Ej: "San Pedro de Atacama" vs "San Pedro")
     */
    fun findCommuneInText(text: String): String? {
        if (text.isBlank()) return null
        
        // Normalizamos input una sola vez
        val normalizedText = normalize(text)
        
        // Buscamos todas las comunas que estén contenidas en el texto
        val matches = communes.filter { commune ->
            normalizedText.contains(normalize(commune))
        }
        
        // Retornamos la más larga (para ganar especificidad)
        return matches.maxByOrNull { it.length }
    }

    /**
     * Busca comunas que coincidan con un texto
     */
    fun search(query: String): List<String> {
        if (query.isBlank()) return communes
        return communes.filter { 
            it.contains(query, ignoreCase = true) 
        }
    }

    
    /**
     * Normaliza un texto: minúsculas, sin tildes, trim
     */
    private fun normalize(text: String): String {
        var normalized = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        normalized = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return normalized.lowercase().trim()
            .replace("comuna de ", "")
            .replace("ciudad de ", "")
    }

    /**
     * Obtiene la comuna más cercana a un nombre (útil para GPS)
     */
    fun findClosest(name: String): String? {
        val normalizedInput = normalize(name)
        
        // 1. Búsqueda exacta (case insensitive)
        communes.find { it.equals(name, ignoreCase = true) }?.let { return it }
        
        // 2. Búsqueda exacta por normalización
        communes.find { normalize(it) == normalizedInput }?.let { return it }
        
        // 3. Búsqueda inteligente (evitar falsos positivos como "Maipo" -> "Isla de Maipo")
        // Solo aceptamos contains parcial si la coincidencia es alta o significativa
        return communes.find { commune ->
            val normalizedCommune = normalize(commune)
            // Si el input está "contenido" en la comuna, verificamos que no sea un match parcial confuso
            // Ej: "Maipo" NO debería match con "Isla de Maipo" solo por contains, a menos que sea la única opción
            // Pero aquí preferimos ser estrictos: 
            
            // Si el geocoder nos da "San Jose de Maipo", normalized="san jose de maipo"
            // Comuna lista: "San José de Maipo", normalized="san jose de maipo" -> Match en paso 2
            
            // Si el geocoder nos da "Maipo" (localidad de Buin), normalized="maipo"
            // Comuna lista: "Isla de Maipo" -> normalized="isla de maipo". contains("maipo") es true.
            // Comuna lista: "San José de Maipo" -> normalized="san jose de maipo". contains("maipo") es true.
            // ERROR: Asignaría cualquiera de las dos.
            
            // Si el input es más corto que la comuna en lista, es peligroso.
            // Solo aceptamos si el input es IGUAL o contiene a la comuna (al revés)
            // Ej: "Comuna de Providencia" (input) contiene "Providencia" (lista) -> OK
            
            normalizedInput.contains(normalizedCommune)
        }
    }
}
