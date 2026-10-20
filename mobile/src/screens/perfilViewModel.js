const TIPOS_DE_PERFIL = {
  ADMIN: 'Administrador',
  GERENTE: 'Gerente',
};

export function montarPerfil(usuario = {}) {
  const tipo = String(usuario.tipo || '').toUpperCase();
  const restauranteNome = String(usuario.restauranteNome || '').trim();
  const semRestaurante = !usuario.restauranteId && !restauranteNome;
  const acessoGlobal = tipo === 'ADMIN' && semRestaurante;

  return {
    nome: String(usuario.nome || '').trim() || 'Usuário LucroPlus',
    email: String(usuario.email || '').trim() || 'E-mail não informado',
    tipo: TIPOS_DE_PERFIL[tipo] || 'Usuário',
    restaurante: acessoGlobal
      ? 'Acesso Administrativo Global'
      : restauranteNome || 'Restaurante não vinculado',
    acessoGlobal,
    semRestaurante,
  };
}
