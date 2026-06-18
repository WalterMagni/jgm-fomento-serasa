'use client';

import React, { useState } from 'react';
import { useStandardTerms } from '@/hooks/useStandardTerms';

const CNPJS = [
  '01.696.866/0001-08',
  '62.173.620/0001-80'
];

const DEFAULT_TERMS: Record<string, string> = {
  '01.696.866/0001-08': '{\n  "titulo": "Termo de Adesão e Condições Gerais",\n  "empresa": "Fomento SC",\n  "clausulas": [\n    "1. O usuário concorda com a análise de crédito.",\n    "2. Os dados serão utilizados apenas para fins de consulta Serasa."\n  ]\n}',
  '62.173.620/0001-80': '{\n  "titulo": "Termo de Confidencialidade",\n  "empresa": "Outra Empresa",\n  "clausulas": [\n    "1. Confidencialidade estrita dos dados consultados.",\n    "2. Proibido compartilhamento com terceiros."\n  ]\n}'
};

function TermEditor({
  selectedCnpj,
  initialText,
  updatedAt,
  onSave,
  isUpdating,
}: {
  selectedCnpj: string;
  initialText: string;
  updatedAt?: string;
  onSave: (params: { cnpj: string; termText: string }) => Promise<unknown>;
  isUpdating: boolean;
}) {
  const [termText, setTermText] = useState<string>(initialText);
  const [isSaved, setIsSaved] = useState(false);

  const handleSave = async () => {
    try {
      await onSave({ cnpj: selectedCnpj, termText });
      setIsSaved(true);
      setTimeout(() => setIsSaved(false), 3000);
    } catch (error) {
      console.error('Erro ao salvar termo:', error);
      alert('Erro ao salvar termo');
    }
  };

  return (
    <div className="bg-white dark:bg-surface-dark rounded-2xl border border-gray-200 dark:border-gray-700 shadow-sm flex flex-col h-[600px]">
      <div className="p-4 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between bg-gray-50/50 dark:bg-gray-800/50 rounded-t-2xl">
        <div className="flex items-center gap-2">
          <span className="material-icons-outlined text-gray-400">data_object</span>
          <span className="font-medium text-gray-700 dark:text-gray-200">
            Conteúdo do Termo (Formato JSON esperado)
          </span>
        </div>
        {updatedAt && (
          <span className="text-xs text-gray-500 dark:text-gray-400">
            Última atualização: {new Date(updatedAt).toLocaleString('pt-BR')}
          </span>
        )}
      </div>
      
      <textarea
        value={termText}
        onChange={(e) => setTermText(e.target.value)}
        className="flex-1 w-full p-4 bg-transparent border-none resize-none focus:ring-0 font-mono text-sm text-gray-800 dark:text-gray-200 leading-relaxed"
        spellCheck="false"
        placeholder="Insira o texto do termo aqui..."
      />
      
      <div className="p-4 border-t border-gray-200 dark:border-gray-700 bg-gray-50/50 dark:bg-gray-800/50 rounded-b-2xl flex items-center justify-between">
        <div className="flex items-center gap-2">
          {isSaved && (
            <div className="flex items-center gap-2 text-green-600 bg-green-50 dark:bg-green-900/30 dark:text-green-400 px-3 py-1.5 rounded-full text-sm font-medium transition-all animate-fade-in">
              <span className="material-icons-outlined text-sm">check_circle</span>
              Salvo com sucesso!
            </div>
          )}
        </div>
        
        <button
          onClick={handleSave}
          disabled={isUpdating}
          className="bg-primary hover:bg-primary-dark text-white px-6 py-2.5 rounded-lg font-medium shadow-sm shadow-primary/30 transition-all active:scale-95 flex items-center gap-2 disabled:opacity-70 disabled:cursor-not-allowed"
        >
          {isUpdating ? (
            <>
              <span className="material-icons-outlined animate-spin text-sm">sync</span>
              Salvando...
            </>
          ) : (
            <>
              <span className="material-icons-outlined text-sm">save</span>
              Salvar Alterações
            </>
          )}
        </button>
      </div>
    </div>
  );
}

export default function StandardTermsPage() {
  const { terms, isLoading, isError, updateTerm, isUpdating } = useStandardTerms();
  
  const [selectedCnpj, setSelectedCnpj] = useState<string>(CNPJS[0]);

  if (isLoading) {
    return (
      <div className="flex h-[calc(100vh-8rem)] items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary"></div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="p-6">
        <div className="bg-red-50 text-red-600 p-4 rounded-lg flex items-center gap-3">
          <span className="material-icons-outlined">error_outline</span>
          <p>Erro ao carregar os termos padrão. Tente novamente mais tarde.</p>
        </div>
      </div>
    );
  }

  const currentTermInfo = terms?.find(t => t.cnpj === selectedCnpj);
  const initialText = currentTermInfo?.termText || DEFAULT_TERMS[selectedCnpj] || '';

  return (
    <div className="p-8 max-w-5xl mx-auto">
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white flex items-center gap-2">
          <span className="material-icons-outlined text-primary">gavel</span>
          Manutenção de Termos Padrão
        </h1>
        <p className="text-gray-500 dark:text-gray-400 mt-2">
          Gerencie e atualize os termos padrão utilizados para as consultas vinculadas a cada CNPJ.
        </p>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
        {/* CNPJ Selection Sidebar */}
        <div className="space-y-4">
          <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-300 uppercase tracking-wider mb-2">
            Selecione o CNPJ
          </h2>
          {CNPJS.map((cnpj) => (
            <button
              key={cnpj}
              onClick={() => setSelectedCnpj(cnpj)}
              className={`w-full text-left p-4 rounded-xl border transition-all duration-200 ${
                selectedCnpj === cnpj
                  ? 'border-primary bg-primary/5 shadow-md'
                  : 'border-gray-200 dark:border-gray-700 hover:border-primary/50 bg-white dark:bg-surface-dark'
              }`}
            >
              <div className="flex items-center gap-3">
                <div className={`p-2 rounded-lg ${selectedCnpj === cnpj ? 'bg-primary text-white' : 'bg-gray-100 dark:bg-gray-800 text-gray-500'}`}>
                  <span className="material-icons-outlined text-sm">business</span>
                </div>
                <span className={`font-medium ${selectedCnpj === cnpj ? 'text-primary dark:text-primary-light' : 'text-gray-700 dark:text-gray-200'}`}>
                  {cnpj}
                </span>
              </div>
            </button>
          ))}
        </div>

        {/* Editor Area */}
        <div className="md:col-span-3">
          <TermEditor
            key={selectedCnpj}
            selectedCnpj={selectedCnpj}
            initialText={initialText}
            updatedAt={currentTermInfo?.updatedAt}
            onSave={updateTerm}
            isUpdating={isUpdating}
          />
        </div>
      </div>
    </div>
  );
}
